package app.venues.booking.service

import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.AddToCartResponse
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.api.mapper.CartMapper
import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartItem
import app.venues.booking.domain.CartSeat
import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatReleasedEvent
import app.venues.booking.event.SeatReservedEvent
import app.venues.booking.repository.CartItemRepository
import app.venues.booking.repository.CartRepository
import app.venues.booking.repository.CartSeatRepository
import app.venues.common.exception.VenuesException
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Cart management service with unified session expiration.
 *
 * Cart acts as a shopping session - all items expire together when the cart expires.
 * Prices are snapshotted at add-to-cart time and remain fixed even if templates change.
 * Cart expiration extends on any activity (add, view, remove).
 */
@Service
@Transactional
class CartService(
    private val cartRepository: CartRepository,
    private val cartSeatRepository: CartSeatRepository,
    private val cartItemRepository: CartItemRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionLevelConfigRepository: SessionLevelConfigRepository,
    private val eventSessionRepository: EventSessionRepository,
    private val cartMapper: CartMapper,
    private val eventPublisher: ApplicationEventPublisher,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        const val CART_EXPIRATION_MINUTES = 15
    }

    /**
     * Finds or creates cart session for the given token and event session.
     * Extends expiration if cart already exists and is active.
     */
    private fun findOrCreateCart(token: UUID?, sessionId: Long, userId: Long? = null): Cart {
        val existingCart = token?.let { cartRepository.findByToken(it) }

        if (existingCart != null) {
            if (existingCart.isExpired()) {
                throw VenuesException.ValidationFailure("Cart has expired. Please start a new cart.")
            }

            if (existingCart.sessionId != sessionId) {
                throw VenuesException.ValidationFailure(
                    "Cannot add items from different event sessions to the same cart"
                )
            }

            // Extend expiration on activity
            existingCart.extendExpiration(CART_EXPIRATION_MINUTES.toLong())
            return cartRepository.save(existingCart)
        }

        // Create new cart session
        val newCart = Cart(
            token = token ?: UUID.randomUUID(),
            userId = userId,
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(CART_EXPIRATION_MINUTES * 60L),
            lastActivityAt = Instant.now()
        )

        return cartRepository.save(newCart)
    }

    /**
     * Adds a seat to cart with atomic reservation and price snapshotting.
     * Creates cart session if first item, extends expiration if cart exists.
     */
    @Transactional
    fun addSeatToCart(request: AddSeatToCartRequest, token: UUID? = null): AddToCartResponse {
        eventSessionRepository.findById(request.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        val seatInfo = seatingApi.getSeatInfoByIdentifier(request.seatIdentifier)
            ?: throw VenuesException.ValidationFailure("Seat not found with identifier: ${request.seatIdentifier}")

        // Atomic operation: Get price if seat is available
        val price = sessionSeatConfigRepository.getSeatPriceIfAvailable(request.sessionId, seatInfo.id)
            ?: throw VenuesException.ValidationFailure("Seat is not available or not priced for this session")

        // Atomic reservation prevents double-booking
        val rowsAffected = sessionSeatConfigRepository.reserveSeatIfAvailable(request.sessionId, seatInfo.id)
        if (rowsAffected == 0) {
            throw VenuesException.ResourceConflict("Seat is not available for reservation")
        }

        // Find or create cart session (extends expiration if exists)
        val cart = findOrCreateCart(token, request.sessionId)

        // Add seat to cart with snapshotted price
        val cartSeat = CartSeat(
            cart = cart,
            sessionId = request.sessionId,
            seatId = seatInfo.id,
            unitPrice = price
        )

        cartSeatRepository.save(cartSeat)
        logger.info { "Seat added to cart: ${request.seatIdentifier}, price=$price, cart=${cart.token}" }

        eventPublisher.publishEvent(
            SeatReservedEvent(
                sessionId = request.sessionId,
                seatIdentifier = request.seatIdentifier,
                reservationToken = cart.token,
                expiresAt = cart.expiresAt.toString()
            )
        )

        return AddToCartResponse(
            token = cart.token,
            message = "Seat added to cart successfully",
            expiresAt = cart.expiresAt.toString()
        )
    }

    /**
     * Adds GA tickets to cart with capacity check and price snapshotting.
     * Creates cart session if first item, extends expiration if cart exists.
     */
    @Transactional
    fun addGAToCart(request: AddGAToCartRequest, token: UUID? = null): AddToCartResponse {
        eventSessionRepository.findById(request.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        val levelInfo = seatingApi.getLevelInfoByIdentifier(request.levelIdentifier)
            ?: throw VenuesException.ValidationFailure("Level not found with identifier: ${request.levelIdentifier}")

        if (!levelInfo.isGeneralAdmission) {
            throw VenuesException.ValidationFailure("Level is not general admission")
        }

        // Atomic operation: Get price if GA tickets are available
        val price =
            sessionLevelConfigRepository.getGAPriceIfAvailable(request.sessionId, levelInfo.id, request.quantity)
                ?: throw VenuesException.ValidationFailure("GA level is not available, not priced, or insufficient capacity")

        // Atomic capacity reservation
        val rowsAffected = sessionLevelConfigRepository.reserveGATicketsIfAvailable(
            request.sessionId, levelInfo.id, request.quantity
        )

        if (rowsAffected == 0) {
            throw VenuesException.ResourceConflict("Not enough tickets available. Requested: ${request.quantity}")
        }

        // Find or create cart session (extends expiration if exists)
        val cart = findOrCreateCart(token, request.sessionId)

        // Add GA tickets to cart with snapshotted price
        val cartItem = CartItem(
            cart = cart,
            sessionId = request.sessionId,
            levelId = levelInfo.id,
            unitPrice = price,
            quantity = request.quantity
        )

        cartItemRepository.save(cartItem)
        logger.info { "GA added to cart: ${request.levelIdentifier}, qty=${request.quantity}, price=$price, cart=${cart.token}" }

        // Fetch updated stats for event publishing
        val levelConfigUpdated = sessionLevelConfigRepository.findBySessionIdAndLevelId(request.sessionId, levelInfo.id)
        val capacity = levelConfigUpdated?.capacity ?: 0
        val availableTickets = capacity - (levelConfigUpdated?.soldCount ?: 0)

        eventPublisher.publishEvent(
            GAAvailabilityChangedEvent(
                sessionId = request.sessionId,
                levelIdentifier = levelInfo.levelIdentifier ?: "",
                levelName = levelInfo.levelName,
                availableTickets = availableTickets,
                totalCapacity = capacity
            )
        )

        return AddToCartResponse(
            token = cart.token,
            message = "GA tickets added to cart successfully",
            expiresAt = cart.expiresAt.toString()
        )
    }

    /**
     * Retrieves cart summary. Touches cart session to track activity.
     * Returns snapshotted prices, not current live prices.
     */
    @Transactional(readOnly = true)
    fun getCartSummary(token: UUID): CartSummaryResponse {
        val cart = cartRepository.findByToken(token)
            ?: throw VenuesException.ResourceNotFound("Cart not found")

        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        // Touch cart to track activity (use separate transaction)
        cart.touch()
        cartRepository.save(cart)

        val seats = cartSeatRepository.findByCart(cart)
        val gaItems = cartItemRepository.findByCart(cart)

        if (seats.isEmpty() && gaItems.isEmpty()) {
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

        val total = seatResponses.sumOf { BigDecimal(it.price) }
            .add(gaItemResponses.sumOf { BigDecimal(it.totalPrice) })

        return cartMapper.toCartSummary(
            token = token,
            seats = seatResponses,
            gaItems = gaItemResponses,
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
        val cart = cartRepository.findByToken(token)
            ?: throw VenuesException.ResourceNotFound("Cart not found")

        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        val cartSeats = cartSeatRepository.findByCart(cart)
        val cartSeat = cartSeats.find {
            seatingApi.getSeatInfo(it.seatId)?.seatIdentifier == seatIdentifier
        } ?: throw VenuesException.ResourceNotFound("Seat not found in cart")

        val seatInfo = seatingApi.getSeatInfo(cartSeat.seatId)
            ?: throw VenuesException.ResourceNotFound("Seat not found")
        val levelInfo = seatingApi.getLevelInfo(seatInfo.levelId)
            ?: throw VenuesException.ResourceNotFound("Level not found")

        // Release inventory
        sessionSeatConfigRepository.findBySessionIdAndSeatId(cart.sessionId, cartSeat.seatId)
            ?.let { config ->
                config.status = app.venues.event.domain.ConfigStatus.AVAILABLE
                sessionSeatConfigRepository.save(config)
            }

        cartSeatRepository.delete(cartSeat)
        logger.info { "Seat removed from cart: $seatIdentifier, cart=${cart.token}" }

        eventPublisher.publishEvent(
            SeatReleasedEvent(
                sessionId = cart.sessionId,
                seatIdentifier = seatIdentifier,
                levelName = levelInfo.levelName
            )
        )
    }

    /**
     * Clears entire cart and releases all inventory.
     */
    @Transactional
    fun clearCart(token: UUID) {
        val cart = cartRepository.findByToken(token)
            ?: throw VenuesException.ResourceNotFound("Cart not found")

        val seats = cartSeatRepository.findByCart(cart)
        val items = cartItemRepository.findByCart(cart)

        // Store seat/level IDs before deleting cart items
        val seatIdsToRelease = seats.map { Pair(cart.sessionId, it.seatId) }
        val levelUpdates = items.map { Triple(cart.sessionId, it.levelId, it.quantity) }

        // Delete cart items first (removes FK references)
        cartSeatRepository.deleteAll(seats)
        cartItemRepository.deleteAll(items)

        // Flush to ensure cart items are deleted before we update configs
        cartSeatRepository.flush()
        cartItemRepository.flush()

        // Now release inventory (no FK constraint issues)
        seatIdsToRelease.forEach { (sessionId, seatId) ->
            sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, seatId)
                ?.let { config ->
                    config.status = app.venues.event.domain.ConfigStatus.AVAILABLE
                    sessionSeatConfigRepository.save(config)
                }
        }

        levelUpdates.forEach { (sessionId, levelId, quantity) ->
            sessionLevelConfigRepository.findBySessionIdAndLevelId(sessionId, levelId)
                ?.let { config ->
                    config.soldCount = maxOf(0, config.soldCount - quantity)
                    sessionLevelConfigRepository.save(config)
                }
        }

        // Finally delete cart
        cartRepository.delete(cart)
        logger.info { "Cart cleared: $token" }
    }

    /**
     * Deletes expired cart sessions and releases all inventory.
     * Called by scheduled cleanup job.
     *
     * All items in expired carts are released atomically.
     */
    @Transactional
    fun deleteExpiredCarts(): Int {
        val now = Instant.now()
        val expiredCarts = cartRepository.findByExpiresAtBefore(now)

        if (expiredCarts.isEmpty()) {
            return 0
        }

        var totalItemsReleased = 0

        expiredCarts.forEach { cart ->
            try {
                val seats = cartSeatRepository.findByCart(cart)
                val items = cartItemRepository.findByCart(cart)

                // Release seat inventory (RESERVED → AVAILABLE)
                seats.forEach { cartSeat ->
                    try {
                        sessionSeatConfigRepository.findBySessionIdAndSeatId(cart.sessionId, cartSeat.seatId)
                            ?.let { config ->
                                config.status = app.venues.event.domain.ConfigStatus.AVAILABLE
                                sessionSeatConfigRepository.save(config)
                            }
                    } catch (e: Exception) {
                        logger.warn { "Failed to release seat ${cartSeat.seatId} from cart ${cart.token}: ${e.message}" }
                    }
                }

                // Release GA capacity (decrement soldCount)
                items.forEach { cartItem ->
                    try {
                        sessionLevelConfigRepository.findBySessionIdAndLevelId(cart.sessionId, cartItem.levelId)
                            ?.let { config ->
                                config.soldCount = maxOf(0, config.soldCount - cartItem.quantity)
                                sessionLevelConfigRepository.save(config)
                            }
                    } catch (e: Exception) {
                        logger.warn { "Failed to release GA capacity for level ${cartItem.levelId} from cart ${cart.token}: ${e.message}" }
                    }
                }

                totalItemsReleased += seats.size + items.size

                // Delete cart (CASCADE deletes items)
                cartRepository.delete(cart)

                logger.info { "Expired cart deleted: ${cart.token}, released ${seats.size} seats and ${items.size} GA items" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to process expired cart ${cart.token}: ${e.message}" }
            }
        }

        if (totalItemsReleased > 0) {
            logger.info { "Cleanup complete: Deleted ${expiredCarts.size} carts, released $totalItemsReleased items" }
        }

        return expiredCarts.size
    }
}
