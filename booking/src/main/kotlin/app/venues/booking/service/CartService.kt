package app.venues.booking.service

import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.AddToCartResponse
import app.venues.booking.api.dto.CartSummaryResponse
import app.venues.booking.api.mapper.CartMapper
import app.venues.booking.domain.CartItem
import app.venues.booking.domain.CartSeat
import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatReleasedEvent
import app.venues.booking.event.SeatReservedEvent
import app.venues.booking.repository.CartItemRepository
import app.venues.booking.repository.CartSeatRepository
import app.venues.common.exception.VenuesException
import app.venues.event.domain.ConfigStatus
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
 * Service for cart management operations.
 *
 * Uses SeatingApi for cross-module communication (Hexagonal Architecture).
 *
 * This is the core of the booking system - handles all cart operations.
 */
@Service
@Transactional
class CartService(
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

    // ===========================================
    // ADD TO CART
    // ===========================================

    /**
     * Add seat to cart.
     */
    fun addSeatToCart(request: AddSeatToCartRequest, token: UUID? = null): AddToCartResponse {
        logger.debug { "Adding seat to cart: sessionId=${request.sessionId}, seatIdentifier=${request.seatIdentifier}" }

        // Validate session exists
        eventSessionRepository.findById(request.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        // Resolve seat identifier to seat ID using SeatingApi (Hexagonal Architecture)
        val seatInfo = seatingApi.getSeatInfoByIdentifier(request.seatIdentifier)
            ?: throw VenuesException.ValidationFailure("Seat not found with identifier: ${request.seatIdentifier}")
        val seatId = seatInfo.id

        // Check if seat config exists and is available
        val seatConfig = sessionSeatConfigRepository.findBySessionIdAndSeatId(request.sessionId, seatId)
            ?: throw VenuesException.ValidationFailure("Seat is not configured for this session")

        if (seatConfig.status != ConfigStatus.AVAILABLE) {
            throw VenuesException.ValidationFailure("Seat is not available (status: ${seatConfig.status})")
        }


        // Check if seat is already in any cart (including expired)
        if (cartSeatRepository.existsBySessionIdAndSeatId(request.sessionId, seatId)) {
            throw VenuesException.ResourceConflict("Seat is already reserved")
        }

        // Use provided token or generate new one
        val reservationToken = token ?: UUID.randomUUID()
        val expiresAt = Instant.now().plusSeconds(CART_EXPIRATION_MINUTES * 60L)

        // Create cart seat
        val cartSeat = CartSeat(
            sessionId = request.sessionId,
            seatId = seatId,
            reservationToken = reservationToken,
            expiresAt = expiresAt
        )

        cartSeatRepository.save(cartSeat)
        logger.info { "Seat added to cart: token=$reservationToken, seatIdentifier=${request.seatIdentifier}" }

        // Fetch level info for event using SeatingApi (Hexagonal Architecture)
        val levelInfo = seatingApi.getLevelInfo(seatInfo.levelId)
            ?: throw VenuesException.ResourceNotFound("Level not found")

        // Publish event for webhook notifications
        eventPublisher.publishEvent(
            SeatReservedEvent(
                sessionId = request.sessionId,
                seatIdentifier = request.seatIdentifier,
                levelName = levelInfo.levelName,
                reservationToken = reservationToken,
                expiresAt = expiresAt.toString()
            )
        )

        return AddToCartResponse(
            token = reservationToken,
            message = "Seat added to cart successfully",
            expiresAt = expiresAt.toString()
        )
    }

    /**
     * Add GA tickets to cart.
     */
    fun addGAToCart(request: AddGAToCartRequest, token: UUID? = null): AddToCartResponse {
        logger.debug { "Adding GA to cart: sessionId=${request.sessionId}, levelIdentifier=${request.levelIdentifier}, quantity=${request.quantity}" }

        // Validate session exists
        eventSessionRepository.findById(request.sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        // Resolve level identifier to level ID using SeatingApi (Hexagonal Architecture)
        // Note: We need to fetch all levels and find by identifier since we can't query by identifier directly
        val levelInfo = seatingApi.getLevelInfoByIdentifier(request.levelIdentifier)
            ?: throw VenuesException.ValidationFailure("Level not found with identifier: ${request.levelIdentifier}")
        val levelId = levelInfo.id

        // Check if level config exists and is available
        val levelConfig = sessionLevelConfigRepository.findBySessionIdAndLevelId(request.sessionId, levelId)
            ?: throw VenuesException.ValidationFailure("GA level is not configured for this session")

        if (levelConfig.status != ConfigStatus.AVAILABLE) {
            throw VenuesException.ValidationFailure("GA level is not available")
        }

        // Verify it's a GA level

        // Verify it's a GA level
        if (!levelInfo.isGeneralAdmission) {
            throw VenuesException.ValidationFailure("Level is not general admission")
        }

        // Check capacity
        val capacity = levelInfo.capacity ?: throw VenuesException.ValidationFailure("Level capacity not set")
        val now = Instant.now()
        val reserved = cartItemRepository.countActiveGATicketsBySessionAndLevel(request.sessionId, levelId, now)

        if (reserved + request.quantity > capacity) {
            throw VenuesException.ResourceConflict("Not enough tickets available. Capacity: $capacity, Reserved: $reserved, Requested: ${request.quantity}")
        }

        // Use provided token or generate new one
        val reservationToken = token ?: UUID.randomUUID()
        val expiresAt = Instant.now().plusSeconds(CART_EXPIRATION_MINUTES * 60L)

        // Create cart item
        val cartItem = CartItem(
            sessionId = request.sessionId,
            levelId = levelId,
            reservationToken = reservationToken,
            quantity = request.quantity,
            expiresAt = expiresAt
        )

        cartItemRepository.save(cartItem)
        logger.info { "GA tickets added to cart: token=$reservationToken, levelIdentifier=${request.levelIdentifier}, quantity=${request.quantity}" }

        // Publish event for webhook notifications
        val availableTickets = capacity - (reserved + request.quantity)
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
            token = reservationToken,
            message = "GA tickets added to cart successfully",
            expiresAt = expiresAt.toString()
        )
    }

    // ===========================================
    // GET CART
    // ===========================================

    /**
     * Get cart summary with all items and pricing.
     */
    @Transactional(readOnly = true)
    fun getCartSummary(token: UUID): CartSummaryResponse {
        logger.debug { "Fetching cart summary: token=$token" }

        val seats = cartSeatRepository.findByReservationToken(token)
        val gaItems = cartItemRepository.findByReservationToken(token)

        if (seats.isEmpty() && gaItems.isEmpty()) {
            throw VenuesException.ResourceNotFound("Cart not found or expired")
        }

        // Check if cart is expired
        val now = Instant.now()
        val allExpired = seats.all { it.expiresAt.isBefore(now) } && gaItems.all { it.expiresAt.isBefore(now) }
        if (allExpired) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        // Get session info (from first item)
        val sessionId = seats.firstOrNull()?.sessionId ?: gaItems.first().sessionId
        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }
        val event = session.event
        val eventTitle = event.title
        val currency = event.currency

        // Map seats with pricing using SeatingApi (Hexagonal Architecture)
        val seatResponses = seats.map { cartSeat ->
            val config = sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, cartSeat.seatId)
                ?: throw VenuesException.ValidationFailure("Seat config not found")

            // Fetch seat and level data from SeatingApi
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
                price = config.price,
                priceTemplateName = config.priceTemplate?.templateName
            )
        }

        // Map GA items with pricing using SeatingApi (Hexagonal Architecture)
        val gaItemResponses = gaItems.map { cartItem ->
            val config = sessionLevelConfigRepository.findBySessionIdAndLevelId(sessionId, cartItem.levelId)
                ?: throw VenuesException.ValidationFailure("Level config not found")

            // Fetch level data from SeatingApi
            val levelInfo = seatingApi.getLevelInfo(cartItem.levelId)
                ?: throw VenuesException.ResourceNotFound("Level not found")

            cartMapper.toCartGAItemResponse(
                cartItem = cartItem,
                levelIdentifier = levelInfo.levelIdentifier,
                levelName = levelInfo.levelName,
                unitPrice = config.price,
                priceTemplateName = config.priceTemplate?.templateName
            )
        }

        // Calculate total
        val seatsTotal = seatResponses.sumOf { BigDecimal(it.price) }
        val gaTotal = gaItemResponses.sumOf { BigDecimal(it.totalPrice) }
        val total = seatsTotal.add(gaTotal)

        // Get latest expiration
        val latestExpiration = (seats.map { it.expiresAt } + gaItems.map { it.expiresAt }).maxOrNull()!!

        return cartMapper.toCartSummary(
            token = token,
            seats = seatResponses,
            gaItems = gaItemResponses,
            totalPrice = total,
            currency = currency,
            expiresAt = latestExpiration.toString(),
            sessionId = sessionId,
            eventTitle = eventTitle
        )
    }

    // ===========================================
    // REMOVE FROM CART
    // ===========================================

    /**
     * Remove seat from cart.
     */
    fun removeSeatFromCart(token: UUID, seatIdentifier: String) {
        logger.debug { "Removing seat from cart: token=$token, seatIdentifier=$seatIdentifier" }

        val cartSeats = cartSeatRepository.findByReservationToken(token)

        // Find cart seat by fetching seat and comparing identifiers using SeatingApi
        val cartSeat = cartSeats.find { cartSeat ->
            val seatInfo = seatingApi.getSeatInfo(cartSeat.seatId)
            seatInfo?.seatIdentifier == seatIdentifier
        } ?: throw VenuesException.ResourceNotFound("Seat not found in cart")

        val sessionId = cartSeat.sessionId

        // Fetch seat and level info for event using SeatingApi (Hexagonal Architecture)
        val seatInfo = seatingApi.getSeatInfo(cartSeat.seatId)
            ?: throw VenuesException.ResourceNotFound("Seat not found")
        val levelInfo = seatingApi.getLevelInfo(seatInfo.levelId)
            ?: throw VenuesException.ResourceNotFound("Level not found")
        val levelName = levelInfo.levelName

        cartSeatRepository.delete(cartSeat)
        logger.info { "Seat removed from cart: token=$token, seatIdentifier=$seatIdentifier" }

        // Publish event for webhook notifications
        eventPublisher.publishEvent(
            SeatReleasedEvent(
                sessionId = sessionId,
                seatIdentifier = seatIdentifier,
                levelName = levelName
            )
        )
    }

    /**
     * Clear entire cart.
     */
    fun clearCart(token: UUID) {
        logger.debug { "Clearing cart: token=$token" }

        cartSeatRepository.deleteByReservationToken(token)
        cartItemRepository.deleteByReservationToken(token)

        logger.info { "Cart cleared: token=$token" }
    }

    // ===========================================
    // CLEANUP
    // ===========================================

    /**
     * Delete expired cart items (called by scheduled job).
     */
    fun deleteExpiredCarts(): Int {
        val now = Instant.now()

        val expiredSeats = cartSeatRepository.findByExpiresAtBefore(now)
        val expiredItems = cartItemRepository.findByExpiresAtBefore(now)

        cartSeatRepository.deleteAll(expiredSeats)
        cartItemRepository.deleteAll(expiredItems)

        val total = expiredSeats.size + expiredItems.size
        if (total > 0) {
            logger.info { "Deleted $total expired cart items (${expiredSeats.size} seats, ${expiredItems.size} GA)" }
        }

        return total
    }
}

