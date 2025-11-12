package app.venues.booking.service

import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.AddToCartResponse
import app.venues.booking.api.dto.CartSummaryResponse
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
     * Adds a seat to cart with atomic reservation and price snapshotting.
     * Creates cart session if first item, extends expiration if cart exists.
     *
     * Uses REPEATABLE_READ isolation to prevent lost updates under high concurrency.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun addSeatToCart(request: AddSeatToCartRequest, token: UUID? = null): AddToCartResponse {
        // Validate session exists
        eventSessionRepository.findById(request.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        // Fetch seat information
        val seatInfo = seatingApi.getSeatInfoByIdentifier(request.seatIdentifier)
            ?: throw VenuesException.ValidationFailure("Seat not found with identifier: ${request.seatIdentifier}")

        // Check if seat belongs to a TABLE_ONLY table
        val levelInfo = seatingApi.getLevelInfo(seatInfo.levelId)
        if (levelInfo?.tableBookingMode == "TABLE_ONLY") {
            throw VenuesException.ValidationFailure(
                "This seat is part of table '${levelInfo.levelName}' which can only be booked as a complete unit"
            )
        }

        // Get or create cart session
        val cart = cartSessionManager.findOrCreateCart(token, request.sessionId)

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

        return AddToCartResponse(
            token = cart.token,
            message = "Seat added to cart successfully",
            expiresAt = cart.expiresAt.toString()
        )
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
        // Validate session exists
        eventSessionRepository.findById(request.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        // Fetch level information
        val levelInfo = seatingApi.getLevelInfoByIdentifier(request.levelIdentifier)
            ?: throw VenuesException.ValidationFailure("Level not found with identifier: ${request.levelIdentifier}")

        if (!levelInfo.isGeneralAdmission) {
            throw VenuesException.ValidationFailure("Level is not general admission")
        }

        // Get or create cart session
        val cart = cartSessionManager.findOrCreateCart(token, request.sessionId)

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

        return AddToCartResponse(
            token = cart.token,
            message = message,
            expiresAt = cart.expiresAt.toString()
        )
    }

    /**
     * Add a complete table to cart.
     * Validates table booking mode, atomically reserves table, blocks individual seats.
     *
     * Uses REPEATABLE_READ isolation to prevent lost updates under high concurrency.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun addTableToCart(sessionId: Long, tableId: Long, token: UUID? = null): AddToCartResponse {
        logger.debug { "Adding table to cart: session=$sessionId, table=$tableId" }

        // Validate session exists
        eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        // Get or create cart
        val cart = cartSessionManager.findOrCreateCart(token, sessionId)

        // Validate table limit
        cartLimitValidator.validateAddTableLimit(cart)

        // Reserve table (atomic + block seats)
        val reservation = tableReservationService.reserveTable(cart, sessionId, tableId)

        // Save to cart
        cartTablePersistence.saveTableToCart(
            cart = cart,
            sessionId = sessionId,
            tableId = reservation.tableId,
            tableName = reservation.tableName,
            seatCount = reservation.seatCount,
            unitPrice = reservation.price
        )


        logger.info { "Table added to cart: ${reservation.tableName}, token=${cart.token}" }

        return AddToCartResponse(
            token = cart.token,
            message = "Table '${reservation.tableName}' (${reservation.seatCount} seats) added to cart successfully",
            expiresAt = cart.expiresAt.toString()
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

        val seats = cartItemPersistence.getAllSeats(cart)
        val gaItems = cartItemPersistence.getAllGAItems(cart)
        val tables = cartTablePersistence.getAllTables(cart)

        if (seats.isEmpty() && gaItems.isEmpty() && tables.isEmpty()) {
            throw VenuesException.ResourceNotFound("Cart is empty")
        }

        val session = eventSessionRepository.findById(cart.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        val seatResponses = seats.map { cartSeat ->
            val config = sessionSeatConfigRepository.findBySessionIdAndSeatId(cart.sessionId, cartSeat.seatId)
                ?: throw VenuesException.ValidationFailure("Seat config not found")
            val seatInfo = seatingApi.getSeatInfo(cartSeat.seatId)
                ?: throw VenuesException.ResourceNotFound("Seat not found")
            val levelInfo = seatingApi.getLevelInfo(seatInfo.levelId)
                ?: throw VenuesException.ResourceNotFound("Level not found")

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

        val gaItemResponses = gaItems.map { cartItem ->
            val config = sessionLevelConfigRepository.findBySessionIdAndLevelId(cart.sessionId, cartItem.levelId)
                ?: throw VenuesException.ValidationFailure("Level config not found")
            val levelInfo = seatingApi.getLevelInfo(cartItem.levelId)
                ?: throw VenuesException.ResourceNotFound("Level not found")

            cartMapper.toCartGAItemResponse(
                cartItem = cartItem,
                levelIdentifier = levelInfo.levelIdentifier,
                levelName = levelInfo.levelName,
                unitPrice = cartItem.unitPrice,
                priceTemplateName = config.priceTemplate?.templateName
            )
        }

        val tableResponses = tables.map { cartTable ->
            val levelInfo = seatingApi.getLevelInfo(cartTable.tableId)
                ?: throw VenuesException.ResourceNotFound("Table level not found")

            cartMapper.toCartTableResponse(
                cartTable = cartTable,
                tableName = levelInfo.levelName,
                seatCount = cartTable.seatCount,
                price = cartTable.unitPrice
            )
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

        val seatInfo = seatingApi.getSeatInfoByIdentifier(seatIdentifier)
            ?: throw VenuesException.ResourceNotFound("Seat not found with identifier: $seatIdentifier")

        // Remove from cart and release inventory
        cartItemPersistence.removeSeat(cart, seatInfo.id, cart.sessionId)
        inventoryReservation.releaseSeat(cart.sessionId, seatInfo.id)
    }

    /**
     * Remove a table from cart and release inventory.
     */
    @Transactional
    fun removeTableFromCart(token: UUID, tableId: Long) {
        val cart = cartSessionManager.getActiveCart(token)

        // Release table (unblocks seats)
        tableReservationService.releaseTable(cart.sessionId, tableId)

        // Remove from cart
        cartTablePersistence.removeTableFromCart(cart, tableId)

        logger.info { "Table $tableId removed from cart $token" }
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
