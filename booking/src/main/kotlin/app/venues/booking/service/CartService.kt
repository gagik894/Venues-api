package app.venues.booking.service

import app.venues.booking.api.dto.*
import app.venues.booking.api.mapper.CartMapper
import app.venues.booking.manager.CartSessionManager
import app.venues.booking.persistence.CartItemPersistence
import app.venues.booking.persistence.CartTablePersistence
import app.venues.booking.persistence.InventoryReservationHandler
import app.venues.booking.repository.CartRepository
import app.venues.booking.validation.CartLimitValidator
import app.venues.common.exception.VenuesException
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Cart management service orchestrating cart operations.
 *
 * Delegates to specialized components for validation, session management,
 * inventory operations, and persistence. Maintains clean separation of concerns.
 */
@Service
@Transactional
class CartService(
    private val cartRepository: CartRepository,
    private val cartSessionManager: CartSessionManager,
    private val cartLimitValidator: CartLimitValidator,
    private val inventoryReservation: InventoryReservationHandler,
    private val cartItemPersistence: CartItemPersistence,
    private val cartTablePersistence: CartTablePersistence,
    private val tableReservationService: TableReservationService,
    private val cartCleanupHelper: CartCleanupHelper,
    private val eventSessionRepository: EventSessionRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionLevelConfigRepository: SessionLevelConfigRepository,
    private val cartMapper: CartMapper,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Validates that an event session exists.
     * @throws VenuesException.ResourceNotFound if session doesn't exist
     */
    private fun validateSessionExists(sessionId: Long) {
        eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }
    }

    /**
     * Gets or creates cart for the given session.
     * Validates that cart belongs to the same session if it already exists.
     */
    private fun getOrCreateCartForSession(token: UUID?, sessionId: Long) =
        cartSessionManager.findOrCreateCart(token, sessionId)

    /**
     * Builds standard success response for add-to-cart operations.
     */
    private fun buildSuccessResponse(cart: app.venues.booking.domain.Cart, message: String) =
        AddToCartResponse(
            token = cart.token,
            message = message,
            expiresAt = cart.expiresAt.toString()
        )


    /**
     * Adds a seat to cart with atomic reservation and price snapshotting.
     * Creates cart session if first item, extends expiration if cart exists.
     *
     * Uses REPEATABLE_READ isolation to prevent lost updates under high concurrency.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun addSeatToCart(request: AddSeatToCartRequest, token: UUID? = null): AddToCartResponse {
        logger.debug { "Adding seat to cart: ${request.seatIdentifier}" }

        // Validate session exists
        validateSessionExists(request.sessionId)

        // Fetch seat information
        val seatInfo = seatingApi.getSeatInfoByIdentifier(request.seatIdentifier)
            ?: throw VenuesException.ValidationFailure("Seat not found with identifier: ${request.seatIdentifier}")

        // Get or create cart session
        val cart = getOrCreateCartForSession(token, request.sessionId)

        // Validate seat not already in cart
        if (cartItemPersistence.checkSeatAlreadyInCart(cart, seatInfo.id)) {
            throw VenuesException.ValidationFailure(
                "Seat ${request.seatIdentifier} is already in your cart"
            )
        }

        // Validate cart limits
        cartLimitValidator.validateAddSeatLimit(cart)

        // Atomic reservation with price snapshot
        val reservation = inventoryReservation.reserveSeat(request.sessionId, seatInfo.id)

        // Persist cart item and publish events
        cartItemPersistence.saveSeatToCart(
            cart = cart,
            sessionId = request.sessionId,
            seatId = seatInfo.id,
            seatIdentifier = request.seatIdentifier,
            price = reservation.price
        )

        logger.info { "Seat ${request.seatIdentifier} added to cart ${cart.token}" }

        return buildSuccessResponse(cart, "Seat added to cart successfully")
    }

    /**
     * Adds GA tickets to cart with smart quantity handling.
     *
     * If GA item for same level exists: Updates quantity (adds to existing)
     * If new level: Creates new cart item
     * Validates quantity limits and capacity.
     *
     * Uses REPEATABLE_READ isolation to prevent lost updates under high concurrency.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun addGAToCart(request: AddGAToCartRequest, token: UUID? = null): AddToCartResponse {
        logger.debug { "Adding GA to cart: ${request.levelIdentifier}, quantity=${request.quantity}" }

        // Validate session exists
        validateSessionExists(request.sessionId)

        // Fetch level information
        val levelInfo = seatingApi.getLevelInfoByIdentifier(request.levelIdentifier)
            ?: throw VenuesException.ValidationFailure("Level not found with identifier: ${request.levelIdentifier}")

        if (!levelInfo.isGeneralAdmission) {
            throw VenuesException.ValidationFailure("Level is not general admission")
        }

        // Get or create cart session
        val cart = getOrCreateCartForSession(token, request.sessionId)

        // Check for existing GA item for this level
        val existingItem = cartItemPersistence.findExistingGAItem(cart, levelInfo.id)

        // Validate quantity limits
        cartLimitValidator.validateAddGALimit(
            cart = cart,
            requestedQuantity = request.quantity,
            existingItemQuantity = existingItem?.quantity
        )

        // Atomic reservation with price snapshot (only for new tickets)
        val reservation = inventoryReservation.reserveGATickets(
            request.sessionId,
            levelInfo.id,
            request.quantity
        )

        // Persist or update cart item and publish events
        val (savedItem, isUpdate) = cartItemPersistence.saveOrUpdateGAItem(
            cart = cart,
            sessionId = request.sessionId,
            levelId = levelInfo.id,
            levelIdentifier = levelInfo.levelIdentifier ?: "",
            levelName = levelInfo.levelName,
            quantityToAdd = request.quantity,
            unitPrice = reservation.unitPrice,
            existingItem = existingItem
        )

        val message = if (isUpdate) {
            "Cart updated: ${request.quantity} ticket(s) added. Total for ${levelInfo.levelName}: ${savedItem.quantity}"
        } else {
            "GA tickets added to cart successfully"
        }

        logger.info { "GA tickets added to cart: ${levelInfo.levelName}, quantity=${savedItem.quantity}, token=${cart.token}" }

        return buildSuccessResponse(cart, message)
    }

    /**
     * Add a complete table to cart.
     * Validates table booking mode, atomically reserves table, blocks individual seats.
     *
     * Uses REPEATABLE_READ isolation to prevent lost updates under high concurrency.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun addTableToCart(request: AddTableToCartRequest, token: UUID? = null): AddToCartResponse {
        logger.debug { "Adding table to cart: session=${request.sessionId}, table=${request.tableIdentifier}" }

        // Validate session exists
        validateSessionExists(request.sessionId)

        // Get or create cart
        val cart = getOrCreateCartForSession(token, request.sessionId)

        // Validate table limit
        cartLimitValidator.validateAddTableLimit(cart)

        // Reserve table (atomic + block seats)
        val reservation = tableReservationService.reserveTable(cart, request.sessionId, request.tableIdentifier)

        // Save to cart
        cartTablePersistence.saveTableToCart(
            cart = cart,
            sessionId = request.sessionId,
            tableId = reservation.tableId,
            tableName = reservation.tableName,
            seatCount = reservation.seatCount,
            unitPrice = reservation.price
        )

        logger.info { "Table ${reservation.tableName} added to cart ${cart.token}" }

        return buildSuccessResponse(
            cart,
            "Table '${reservation.tableName}' (${reservation.seatCount} seats) added to cart successfully"
        )
    }

    /**
     * Retrieves cart summary. Touches cart session to track activity.
     * Returns snapshotted prices, not current live prices.
     */
    @Transactional(readOnly = true)
    fun getCartSummary(token: UUID): CartSummaryResponse {
        val cart = cartSessionManager.getActiveCart(token)
        cartSessionManager.touchCart(cart)

        val session = eventSessionRepository.findById(cart.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        // 1. Get all cart items from local DB
        val seats = cartItemPersistence.getAllSeats(cart)
        val gaItems = cartItemPersistence.getAllGAItems(cart)
        val tables = cartTablePersistence.getAllTables(cart)

        if (seats.isEmpty() && gaItems.isEmpty() && tables.isEmpty()) {
            throw VenuesException.ResourceNotFound("Cart is empty")
        }

        // 2. Collect all unique IDs needed for batch fetching
        val seatIds = seats.map { it.seatId }.distinct()
        val gaLevelIds = gaItems.map { it.levelId }.distinct()
        val tableIds = tables.map { it.tableId }.distinct()

        // 3. Make batch calls to repositories and APIs
        // NOTE: Assumes these '...In' methods exist on the respective repositories
        val seatConfigs = sessionSeatConfigRepository.findBySessionIdAndSeatIdIn(cart.sessionId, seatIds)
            .associateBy { it.seatId }
        val levelConfigs = sessionLevelConfigRepository.findBySessionIdAndLevelIdIn(cart.sessionId, gaLevelIds)
            .associateBy { it.levelId }

        // NOTE: Assumes SeatingApi supports batch operations
        val seatInfoMap = seatingApi.getSeatInfoBatch(seatIds).associateBy { it.id }

        val allLevelIds = (
                seatInfoMap.values.map { it.levelId } + gaLevelIds + tableIds
                ).distinct()
        val levelInfoMap = seatingApi.getLevelInfoBatch(allLevelIds).associateBy { it.id }

        // 4. Map results in memory (no more calls inside loops)
        val seatResponses = seats.mapNotNull { cartSeat ->
            val config = seatConfigs[cartSeat.seatId]
            val seatInfo = seatInfoMap[cartSeat.seatId]
            val levelInfo = seatInfo?.let { levelInfoMap[it.levelId] }

            if (config == null || seatInfo == null || levelInfo == null) {
                logger.warn { "Missing data for cart seat: ${cartSeat.seatId}" }
                null // Skip item if data is inconsistent
            } else {
                cartMapper.toCartSeatResponse(
                    cartSeat = cartSeat,
                    seatIdentifier = seatInfo.seatIdentifier,
                    seatNumber = seatInfo.seatNumber,
                    rowLabel = seatInfo.rowLabel,
                    levelName = levelInfo.levelName,
                    levelIdentifier = levelInfo.levelIdentifier,
                    price = cartSeat.unitPrice,
                    priceTemplateName = config.priceTemplate?.templateName
                )
            }
        }

        val gaItemResponses = gaItems.mapNotNull { cartItem ->
            val config = levelConfigs[cartItem.levelId]
            val levelInfo = levelInfoMap[cartItem.levelId]

            if (config == null || levelInfo == null) {
                logger.warn { "Missing data for cart GA item: ${cartItem.levelId}" }
                null
            } else {
                cartMapper.toCartGAItemResponse(
                    cartItem = cartItem,
                    levelIdentifier = levelInfo.levelIdentifier,
                    levelName = levelInfo.levelName,
                    unitPrice = cartItem.unitPrice,
                    priceTemplateName = config.priceTemplate?.templateName
                )
            }
        }

        val tableResponses = tables.mapNotNull { cartTable ->
            val levelInfo = levelInfoMap[cartTable.tableId] // Table is a level
            if (levelInfo == null) {
                logger.warn { "Missing data for cart table item: ${cartTable.tableId}" }
                null
            } else {
                cartMapper.toCartTableResponse(
                    cartTable = cartTable,
                    tableName = levelInfo.levelName,
                    seatCount = cartTable.seatCount,
                    price = cartTable.unitPrice
                )
            }
        }

        val total = seatResponses.sumOf { BigDecimal(it.price) }
            .add(gaItemResponses.sumOf { BigDecimal(it.totalPrice) })
            .add(tableResponses.sumOf { it.price })

        return cartMapper.toCartSummary(
            token = token,
            seats = seatResponses,
            gaItems = gaItemResponses,
            tables = tableResponses,
            totalPrice = total,
            currency = session.event.currency,
            expiresAt = cart.expiresAt.toString(),
            sessionId = cart.sessionId,
            eventTitle = session.event.title
        )
    }

    /**
     * Removes a specific seat from cart and releases inventory.
     */
    @Transactional
    fun removeSeatFromCart(token: UUID, seatIdentifier: String) {
        val cart = cartSessionManager.getActiveCart(token)

        // 1. Get info from external API ONCE
        val seatInfo = seatingApi.getSeatInfoByIdentifier(seatIdentifier)
            ?: throw VenuesException.ResourceNotFound("Seat not found with identifier: $seatIdentifier")

        val levelInfo = seatingApi.getLevelInfo(seatInfo.levelId)
            ?: throw VenuesException.ResourceNotFound("Level not found for seat")

        // 2. Remove from cart (passing info to avoid second API call)
        cartItemPersistence.removeSeat(
            cart = cart,
            seatId = seatInfo.id,
            sessionId = cart.sessionId,
            seatIdentifier = seatInfo.seatIdentifier, // Pass info
            levelName = levelInfo.levelName           // Pass info
        )

        // 3. Release inventory
        inventoryReservation.releaseSeat(cart.sessionId, seatInfo.id)
    }

    /**
     * Updates quantity of GA tickets in cart with atomic inventory adjustment.
     * If new quantity is 0, removes the GA item from cart.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun updateGAQuantity(token: UUID, levelIdentifier: String, newQuantity: Int) {
        if (newQuantity <= 0) {
            // Treat setting quantity to 0 as a removal
            removeGAFromCart(token, levelIdentifier)
            return
        }

        val cart = cartSessionManager.getActiveCart(token)

        val levelInfo = seatingApi.getLevelInfoByIdentifier(levelIdentifier)
            ?: throw VenuesException.ResourceNotFound("Level not found: $levelIdentifier")

        val existingItem = cartItemPersistence.findExistingGAItem(cart, levelInfo.id)
            ?: throw VenuesException.ResourceNotFound("GA item not found in cart")

        val quantityDelta = newQuantity - existingItem.quantity

        // Only validate limits if we are adding more tickets
        if (quantityDelta > 0) {
            // Validate cart limits
            cartLimitValidator.validateAddGALimit(
                cart = cart,
                requestedQuantity = quantityDelta,
                existingItemQuantity = existingItem.quantity
            )
        }

        // Atomically adjust inventory reservation
        inventoryReservation.adjustGATickets(
            sessionId = cart.sessionId,
            levelId = levelInfo.id,
            newQuantity = newQuantity,
            oldQuantity = existingItem.quantity
        )

        // Update persistence
        cartItemPersistence.updateGAItemQuantity(
            item = existingItem,
            newQuantity = newQuantity,
            levelIdentifier = levelInfo.levelIdentifier ?: "",
            levelName = levelInfo.levelName
        )

        logger.info { "GA quantity updated in cart ${cart.token}: level=$levelIdentifier, new_qty=$newQuantity" }
    }

    /**
     * Removes all GA tickets for a level from cart and releases inventory.
     */
    @Transactional
    fun removeGAFromCart(token: UUID, levelIdentifier: String) {
        val cart = cartSessionManager.getActiveCart(token)

        val levelInfo = seatingApi.getLevelInfoByIdentifier(levelIdentifier)
            ?: throw VenuesException.ResourceNotFound("Level not found: $levelIdentifier")

        val existingItem = cartItemPersistence.findExistingGAItem(cart, levelInfo.id)
            ?: throw VenuesException.ResourceNotFound("GA item not found in cart")

        val quantityToRelease = existingItem.quantity

        // Remove from persistence
        cartItemPersistence.removeGAItem(
            item = existingItem,
            levelIdentifier = levelInfo.levelIdentifier ?: "",
            levelName = levelInfo.levelName
        )

        // Release inventory
        inventoryReservation.releaseGATickets(cart.sessionId, levelInfo.id, quantityToRelease)

        logger.info { "GA item removed from cart ${cart.token}: level=$levelIdentifier, qty_released=$quantityToRelease" }
    }

    /**
     * Remove a table from cart and release inventory.
     */
    @Transactional
    fun removeTableFromCart(token: UUID, tableIdentifier: String) {
        val cart = cartSessionManager.getActiveCart(token)

        // 1. Get table info to find its ID
        val tableInfo = seatingApi.getLevelInfoByIdentifier(tableIdentifier)
            ?: throw VenuesException.ResourceNotFound("Table not found: $tableIdentifier")

        val tableId = tableInfo.id

        // 2. Release table (unblocks seats)
        tableReservationService.releaseTable(cart.sessionId, tableId)

        // 3. Remove from cart persistence
        cartTablePersistence.removeTableFromCart(cart, tableId)

        logger.info { "Table $tableIdentifier (ID: $tableId) removed from cart $token" }
    }

    /**
     * Clears entire cart and releases all inventory.
     */
    @Transactional
    fun clearCart(token: UUID) {
        val cart = cartSessionManager.getActiveCart(token)

        val seats = cartItemPersistence.getAllSeats(cart)
        val gaItems = cartItemPersistence.getAllGAItems(cart)
        val tables = cartTablePersistence.getAllTables(cart)

        // Store IDs before deletion
        val seatIdsToRelease = seats.map { Pair(cart.sessionId, it.seatId) }
        val levelUpdates = gaItems.map { Triple(cart.sessionId, it.levelId, it.quantity) }
        val tableIdsToRelease = tables.map { Pair(cart.sessionId, it.tableId) }

        // Delete all cart items (with flush to ensure FK cleanup)
        cartItemPersistence.deleteAllItems(cart)
        cartTablePersistence.clearAllTables(cart)

        // Release inventory
        seatIdsToRelease.forEach { (sessionId, seatId) ->
            inventoryReservation.releaseSeat(sessionId, seatId)
        }

        levelUpdates.forEach { (sessionId, levelId, quantity) ->
            inventoryReservation.releaseGATickets(sessionId, levelId, quantity)
        }

        tableIdsToRelease.forEach { (sessionId, tableId) ->
            tableReservationService.releaseTable(sessionId, tableId)
        }

        // Delete cart
        cartRepository.delete(cart)
        logger.info { "Cart cleared: $token" }
    }

    /**
     * Deletes expired cart sessions and releases all inventory.
     * Called by scheduled cleanup job.
     */
    fun deleteExpiredCarts(): Int {
        val now = Instant.now()
        val expiredCarts = cartRepository.findByExpiresAtBefore(now)

        if (expiredCarts.isEmpty()) {
            return 0
        }

        var totalItemsReleased = 0
        var successfulDeletions = 0

        // Process each cart in its own transaction via helper component
        expiredCarts.forEach { cart ->
            val result = cartCleanupHelper.deleteSingleCart(cart)
            if (result != null) {
                totalItemsReleased += result.itemsReleased
                successfulDeletions++
            }
        }

        if (totalItemsReleased > 0) {
            logger.info { "Cleanup complete: Deleted $successfulDeletions carts, released $totalItemsReleased items" }
        }

        return successfulDeletions
    }
}
