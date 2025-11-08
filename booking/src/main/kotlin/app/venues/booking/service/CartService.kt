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
 * Cart management service implementing "Snapshot at Add to Cart" pattern.
 *
 * Prices are captured from templates when items are added and remain fixed
 * even if live prices change later. This ensures price consistency for users.
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

    /**
     * Adds a seat to cart with atomic reservation and price snapshotting.
     *
     * @throws app.venues.common.exception.VenuesException.ValidationFailure if seat not configured, not priced, or unavailable
     * @throws app.venues.common.exception.VenuesException.ResourceConflict if seat taken by another user during reservation
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

        // Snapshot price from template
        val reservationToken = token ?: UUID.randomUUID()
        val cartSeat = CartSeat(
            sessionId = request.sessionId,
            seatId = seatInfo.id,
            unitPrice = price,
            reservationToken = reservationToken,
            expiresAt = Instant.now().plusSeconds(CART_EXPIRATION_MINUTES * 60L)
        )

        cartSeatRepository.save(cartSeat)
        logger.info { "Seat reserved: ${request.seatIdentifier}, price=${cartSeat.unitPrice}" }

        eventPublisher.publishEvent(
            SeatReservedEvent(
                sessionId = request.sessionId,
                seatIdentifier = request.seatIdentifier,
                reservationToken = reservationToken,
                expiresAt = cartSeat.expiresAt.toString()
            )
        )

        return AddToCartResponse(
            token = reservationToken,
            message = "Seat added to cart successfully",
            expiresAt = cartSeat.expiresAt.toString()
        )
    }

    /**
     * Adds GA tickets to cart with capacity check and price snapshotting.
     *
     * @throws app.venues.common.exception.VenuesException.ValidationFailure if level not configured, not priced, or not GA
     * @throws app.venues.common.exception.VenuesException.ResourceConflict if insufficient capacity available
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
            throw VenuesException.ResourceConflict(
                "Not enough tickets available. Requested: ${request.quantity}"
            )
        }

        // Snapshot price from template
        val reservationToken = token ?: UUID.randomUUID()
        val cartItem = CartItem(
            sessionId = request.sessionId,
            levelId = levelInfo.id,
            unitPrice = price,
            reservationToken = reservationToken,
            quantity = request.quantity,
            expiresAt = Instant.now().plusSeconds(CART_EXPIRATION_MINUTES * 60L)
        )

        cartItemRepository.save(cartItem)
        logger.info { "GA reserved: ${request.levelIdentifier}, qty=${request.quantity}, price=${cartItem.unitPrice}" }

        // Fetch updated stats for event publishing (outside critical path)
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
            token = reservationToken,
            message = "GA tickets added to cart successfully",
            expiresAt = cartItem.expiresAt.toString()
        )
    }

    /**
     * Retrieves cart summary using snapshotted prices, not current live prices.
     */
    @Transactional(readOnly = true)
    fun getCartSummary(token: UUID): CartSummaryResponse {
        val seats = cartSeatRepository.findByReservationToken(token)
        val gaItems = cartItemRepository.findByReservationToken(token)

        if (seats.isEmpty() && gaItems.isEmpty()) {
            throw VenuesException.ResourceNotFound("Cart not found or expired")
        }

        val now = Instant.now()
        val allExpired = seats.all { it.expiresAt.isBefore(now) } && gaItems.all { it.expiresAt.isBefore(now) }
        if (allExpired) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        val sessionId = seats.firstOrNull()?.sessionId ?: gaItems.first().sessionId
        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found") }

        val seatResponses = seats.map { cartSeat ->
            val config = sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, cartSeat.seatId)
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
            val config = sessionLevelConfigRepository.findBySessionIdAndLevelId(sessionId, cartItem.levelId)
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

        val latestExpiration = (seats.map { it.expiresAt } + gaItems.map { it.expiresAt }).maxOrNull()
            ?: throw VenuesException.ValidationFailure("Cart has no valid expiration")

        return cartMapper.toCartSummary(
            token = token,
            seats = seatResponses,
            gaItems = gaItemResponses,
            totalPrice = total,
            currency = session.event.currency,
            expiresAt = latestExpiration.toString(),
            sessionId = sessionId,
            eventTitle = session.event.title
        )
    }

    fun removeSeatFromCart(token: UUID, seatIdentifier: String) {
        val cartSeats = cartSeatRepository.findByReservationToken(token)
        val cartSeat = cartSeats.find {
            seatingApi.getSeatInfo(it.seatId)?.seatIdentifier == seatIdentifier
        } ?: throw VenuesException.ResourceNotFound("Seat not found in cart")

        val seatInfo = seatingApi.getSeatInfo(cartSeat.seatId)
            ?: throw VenuesException.ResourceNotFound("Seat not found")
        val levelInfo = seatingApi.getLevelInfo(seatInfo.levelId)
            ?: throw VenuesException.ResourceNotFound("Level not found")

        cartSeatRepository.delete(cartSeat)
        logger.info { "Seat removed: $seatIdentifier" }

        eventPublisher.publishEvent(
            SeatReleasedEvent(
                sessionId = cartSeat.sessionId,
                seatIdentifier = seatIdentifier,
                levelName = levelInfo.levelName
            )
        )
    }

    fun clearCart(token: UUID) {
        cartSeatRepository.deleteByReservationToken(token)
        cartItemRepository.deleteByReservationToken(token)
        logger.info { "Cart cleared: $token" }
    }

    /**
     * Deletes expired cart items and releases inventory.
     * Called by scheduled cleanup job.
     */
    fun deleteExpiredCarts(): Int {
        val now = Instant.now()
        val expiredSeats = cartSeatRepository.findByExpiresAtBefore(now)
        val expiredItems = cartItemRepository.findByExpiresAtBefore(now)

        // Release seat inventory (RESERVED → AVAILABLE)
        expiredSeats.forEach { cartSeat ->
            try {
                sessionSeatConfigRepository.findBySessionIdAndSeatId(cartSeat.sessionId, cartSeat.seatId)
                    ?.let { config ->
                        config.status = app.venues.event.domain.ConfigStatus.AVAILABLE
                        sessionSeatConfigRepository.save(config)
                    }
            } catch (e: Exception) {
                logger.warn { "Failed to release seat ${cartSeat.seatId}: ${e.message}" }
            }
        }

        // Release GA capacity (decrement soldCount)
        expiredItems.forEach { cartItem ->
            try {
                sessionLevelConfigRepository.findBySessionIdAndLevelId(cartItem.sessionId, cartItem.levelId)
                    ?.let { config ->
                        config.soldCount = maxOf(0, config.soldCount - cartItem.quantity)
                        sessionLevelConfigRepository.save(config)
                    }
            } catch (e: Exception) {
                logger.warn { "Failed to release GA capacity for level ${cartItem.levelId}: ${e.message}" }
            }
        }

        // Delete cart records
        cartSeatRepository.deleteAll(expiredSeats)
        cartItemRepository.deleteAll(expiredItems)

        val total = expiredSeats.size + expiredItems.size
        if (total > 0) {
            logger.info { "Deleted $total expired cart items and released inventory" }
        }

        return total
    }
}
