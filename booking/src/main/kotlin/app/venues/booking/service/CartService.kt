package app.venues.booking.service

import app.venues.booking.api.CartApi
import app.venues.booking.api.dto.*
import app.venues.booking.manager.CartSessionManager
import app.venues.booking.persistence.CartItemPersistence
import app.venues.booking.persistence.CartTablePersistence
import app.venues.booking.persistence.InventoryReservationHandler
import app.venues.booking.repository.CartRepository
import app.venues.booking.validation.CartLimitValidator
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Cart management service for **Command** operations.
 *
 * This service is responsible for all mutations (changes) to a cart,
 * such as adding, updating, or removing items.
 *
 * All read operations are handled by `CartQueryService`.
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
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi,
    private val validator: Validator
) : CartApi {
    private val logger = KotlinLogging.logger {}

    /**
     * Validates that an event session exists.
     * @throws VenuesException.ResourceNotFound if session doesn't exist
     */
    private fun validateSessionExists(sessionId: UUID) {
        eventApi.getEventSessionInfo(sessionId)
            ?: throw VenuesException.ResourceNotFound("Session not found")
    }

    /**
     * Gets or creates cart for the given session.
     * Validates that cart belongs to the same session if it already exists.
     */
    private fun getOrCreateCartForSession(token: UUID?, sessionId: UUID) =
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
    override fun addSeatToCart(request: AddSeatToCartRequest, token: UUID?): AddToCartResponse {
        logger.debug { "Adding seat to cart: ${request.code}" }

        val violations = validator.validate(request)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }

        // Validate session exists
        validateSessionExists(request.sessionId)

        // Fetch seat information by code (seat identifier is the seat code)
        val seatInfo = seatingApi.getSeatInfoByCode(request.code)
            ?: throw VenuesException.ValidationFailure("Seat not found with code: ${request.code}")

        // Get or create cart session
        val cart = getOrCreateCartForSession(token, request.sessionId)

        // Validate seat not already in cart
        if (cartItemPersistence.checkSeatAlreadyInCart(cart, seatInfo.id)) {
            throw VenuesException.ValidationFailure(
                "Seat ${request.code} is already in your cart"
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
            seatIdentifier = request.code,
            price = reservation.price
        )

        logger.info { "Seat ${request.code} added to cart ${cart.token}" }

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
    override fun addGAToCart(request: AddGAToCartRequest, token: UUID?): AddToCartResponse {
        logger.debug { "Adding GA to cart: ${request.code}, quantity=${request.quantity}" }

        val violations = validator.validate(request)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }

        // Validate session exists
        validateSessionExists(request.sessionId)

        // Fetch GA area information by code
        val gaInfo = seatingApi.getGaInfoByCode(request.code)
            ?: throw VenuesException.ValidationFailure("GA area not found with code: ${request.code}")

        // Get or create cart session
        val cart = getOrCreateCartForSession(token, request.sessionId)

        // Check for existing GA item for this area
        val existingItem = cartItemPersistence.findExistingGAItem(cart, gaInfo.id)

        // Validate quantity limits
        cartLimitValidator.validateAddGALimit(
            cart = cart,
            requestedQuantity = request.quantity,
            existingItemQuantity = existingItem?.quantity
        )

        // Atomic reservation with price snapshot (only for new tickets)
        val reservation = inventoryReservation.reserveGATickets(
            request.sessionId,
            gaInfo.id,
            request.quantity
        )

        // Persist or update cart item and publish events
        val (savedItem, isUpdate) = cartItemPersistence.saveOrUpdateGAItem(
            cart = cart,
            sessionId = request.sessionId,
            gaAreaId = gaInfo.id,
            levelIdentifier = gaInfo.code,
            levelName = gaInfo.name,
            quantityToAdd = request.quantity,
            unitPrice = reservation.unitPrice,
            existingItem = existingItem
        )

        val message = if (isUpdate) {
            "Cart updated: ${request.quantity} ticket(s) added. Total for ${gaInfo.name}: ${savedItem.quantity}"
        } else {
            "GA tickets added to cart successfully"
        }

        logger.info { "GA tickets added to cart: ${gaInfo.name}, quantity=${savedItem.quantity}, token=${cart.token}" }

        return buildSuccessResponse(cart, message)
    }

    /**
     * Add a complete table to cart.
     * Validates table booking mode, atomically reserves table, blocks individual seats.
     *
     * Uses REPEATABLE_READ isolation to prevent lost updates under high concurrency.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    override fun addTableToCart(request: AddTableToCartRequest, token: UUID?): AddToCartResponse {
        logger.debug { "Adding table to cart: session=${request.sessionId}, table=${request.code}" }

        val violations = validator.validate(request)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }

        // Validate session exists
        validateSessionExists(request.sessionId)

        // Get or create cart
        val cart = getOrCreateCartForSession(token, request.sessionId)

        // Validate table limit
        cartLimitValidator.validateAddTableLimit(cart)

        // Reserve table (atomic + block seats)
        val reservation = tableReservationService.reserveTable(cart, request.sessionId, request.code)

        // Save to cart
        cartTablePersistence.saveTableToCart(
            cart = cart,
            sessionId = request.sessionId,
            tableId = reservation.tableId,
            tableName = reservation.tableName,
            unitPrice = reservation.price
        )

        logger.info { "Table ${reservation.tableName} added to cart ${cart.token}" }

        return buildSuccessResponse(
            cart,
            "Table '${reservation.tableName}' (${reservation.seatCount} seats) added to cart successfully"
        )
    }

    /**
     * Removes a specific seat from cart and releases inventory.
     */
    @Transactional
    override fun removeSeatFromCart(token: UUID, seatIdentifier: String) {
        val cart = cartSessionManager.getActiveCart(token)

        // Get seat info from seating API
        val seatInfo = seatingApi.getSeatInfoByCode(seatIdentifier)
            ?: throw VenuesException.ResourceNotFound("Seat not found with code: $seatIdentifier")

        // Remove from cart
        cartItemPersistence.removeSeat(
            cart = cart,
            seatId = seatInfo.id,
            sessionId = cart.sessionId,
            seatIdentifier = seatInfo.code,
        )

        // Release inventory
        inventoryReservation.releaseSeat(cart.sessionId, seatInfo.id)
    }

    /**
     * Updates quantity of GA tickets in cart with atomic inventory adjustment.
     * If new quantity is 0, removes the GA item from cart.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    override fun updateGAQuantity(token: UUID, levelIdentifier: String, request: UpdateGAQuantityRequest) {
        val violations = validator.validate(request)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }
        val newQuantity = request.quantity
        if (newQuantity <= 0) {
            // Treat setting quantity to 0 as a removal
            removeGAFromCart(token, levelIdentifier)
            return
        }

        val cart = cartSessionManager.getActiveCart(token)

        val gaInfo = seatingApi.getGaInfoByCode(levelIdentifier)
            ?: throw VenuesException.ResourceNotFound("GA area not found: $levelIdentifier")

        val existingItem = cartItemPersistence.findExistingGAItem(cart, gaInfo.id)
            ?: throw VenuesException.ResourceNotFound("GA item not found in cart")

        // Calculate the *change* in quantity
        val quantityDelta = newQuantity - existingItem.quantity

        // Only validate limits if we are *adding* more tickets
        if (quantityDelta > 0) {
            cartLimitValidator.validateAddGALimit(
                cart = cart,
                requestedQuantity = quantityDelta,
                existingItemQuantity = existingItem.quantity
            )
        }

        // Atomically adjust inventory reservation
        inventoryReservation.adjustGATickets(
            sessionId = cart.sessionId,
            gaAreaId = gaInfo.id,
            newQuantity = newQuantity,
            oldQuantity = existingItem.quantity
        )

        // Update persistence
        cartItemPersistence.updateGAItemQuantity(
            item = existingItem,
            newQuantity = newQuantity,
            levelIdentifier = gaInfo.code,
            levelName = gaInfo.name
        )

        logger.info { "GA quantity updated in cart ${cart.token}: level=$levelIdentifier, new_qty=$newQuantity" }
    }

    /**
     * Removes all GA tickets for a level from cart and releases inventory.
     */
    @Transactional
    override fun removeGAFromCart(token: UUID, levelIdentifier: String) {
        val cart = cartSessionManager.getActiveCart(token)

        val gaInfo = seatingApi.getGaInfoByCode(levelIdentifier)
            ?: throw VenuesException.ResourceNotFound("GA area not found: $levelIdentifier")

        val existingItem = cartItemPersistence.findExistingGAItem(cart, gaInfo.id)
            ?: throw VenuesException.ResourceNotFound("GA item not found in cart")

        val quantityToRelease = existingItem.quantity

        // Remove from persistence
        cartItemPersistence.removeGAItem(
            item = existingItem,
            levelIdentifier = gaInfo.code,
            levelName = gaInfo.name
        )

        // Release inventory
        inventoryReservation.releaseGATickets(cart.sessionId, gaInfo.id, quantityToRelease)

        logger.info { "GA item removed from cart ${cart.token}: level=$levelIdentifier, qty_released=$quantityToRelease" }
    }

    /**
     * Remove a table from cart and release inventory.
     */
    @Transactional
    override fun removeTableFromCart(token: UUID, tableIdentifier: String) {
        val cart = cartSessionManager.getActiveCart(token)

        // Get table info by code
        val tableInfo = seatingApi.getTableInfoByCode(tableIdentifier)
            ?: throw VenuesException.ResourceNotFound("Table not found: $tableIdentifier")

        val tableId = tableInfo.id

        // Release table (unblocks seats)
        tableReservationService.releaseTable(cart.sessionId, tableId)

        // Remove from cart persistence
        cartTablePersistence.removeTableFromCart(cart, tableId)

        logger.info { "Table $tableIdentifier (ID: $tableId) removed from cart $token" }
    }

    /**
     * Clears entire cart and releases all inventory using batch operations.
     * Optimized for high-volume scenarios (10K+ seats).
     *
     * Performance: O(1) instead of O(n) - uses bulk database operations.
     */
    @Transactional
    override fun clearCart(token: UUID) {
        val cart = cartSessionManager.getActiveCart(token)

        // 1. Load all items ONCE (single query per type)
        val seats = cartItemPersistence.getAllSeats(cart)
        val gaItems = cartItemPersistence.getAllGAItems(cart)
        val tables = cartTablePersistence.getAllTables(cart)

        // 2. Extract IDs for batch operations
        val seatIds = seats.map { it.seatId }
        val gaUpdates = gaItems.map { Pair(it.gaAreaId, it.quantity) }
        val tableIds = tables.map { it.tableId }

        // 3. Delete cart items FIRST (ensures FK cleanup before inventory release)
        cartItemPersistence.deleteAllItems(cart)
        cartTablePersistence.clearAllTables(cart)

        // 4. Release inventory in BATCH operations (critical for performance)
        if (seatIds.isNotEmpty()) {
            inventoryReservation.releaseSeatsBatch(cart.sessionId, seatIds)
        }

        if (gaUpdates.isNotEmpty()) {
            inventoryReservation.releaseGATicketsBatch(cart.sessionId, gaUpdates)
        }

        if (tableIds.isNotEmpty()) {
            tableReservationService.releaseTablesBatch(cart.sessionId, tableIds)
        }

        // 5. Delete cart entity
        cartRepository.delete(cart)

        logger.info {
            "Cart cleared: token=$token, seats=${seatIds.size}, GA=${gaUpdates.size}, tables=${tableIds.size}"
        }
    }
}
