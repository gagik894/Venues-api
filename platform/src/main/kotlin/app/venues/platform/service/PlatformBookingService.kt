package app.venues.platform.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.dto.AddGAToCartRequest
import app.venues.booking.api.dto.AddSeatToCartRequest
import app.venues.booking.api.dto.AddTableToCartRequest
import app.venues.booking.manager.CartSessionManager
import app.venues.booking.repository.CartRepository
import app.venues.common.exception.VenuesException
import app.venues.platform.api.dto.*
import app.venues.platform.repository.PlatformRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Platform booking service implementing secure /hold, /checkout, /confirm flow.
 *
 * Security features:
 * - Cart-platform binding (audit fix CRIT-01, CRIT-02)
 * - Atomic operations to prevent race conditions (audit fix CRIT-03)
 * - Transaction boundaries for consistency (audit fix STATE-01)
 * - Pessimistic locking for booking confirmation (audit fix HIGH-01)
 */
@Service
@Transactional
class PlatformBookingService(
    private val platformRepository: PlatformRepository,
    private val cartRepository: CartRepository,
    private val cartSessionManager: CartSessionManager,
    private val cartApi: CartApi,
    private val cartQueryApi: CartQueryApi,
    private val bookingApi: BookingApi,
    private val seatingApi: SeatingApi,
    private val rateLimitService: PlatformRateLimitService,
    private val idempotencyService: PlatformIdempotencyService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Hold inventory - create cart and reserve seats/GA/tables.
     */
    fun hold(
        platformId: UUID,
        request: PlatformHoldRequest,
        idempotencyKey: String?
    ): PlatformHoldResponse {
        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = "hold",
            responseType = PlatformHoldResponse::class.java
        ) {
            rateLimitService.enforce(platformId, 100)
            executeHold(platformId, request)
        }
    }

    private fun executeHold(platformId: UUID, request: PlatformHoldRequest): PlatformHoldResponse {
        // Verify platform exists and is active
        val platform = platformRepository.findById(platformId).orElseThrow {
            VenuesException.ResourceNotFound("Platform not found")
        }

        if (!platform.isActive()) {
            throw VenuesException.ValidationFailure("Platform is not active")
        }

        // Find or create cart
        val holdTokenParam = request.holdToken  // Local val for smart cast
        val cart = if (holdTokenParam != null) {
            val existingCart = cartRepository.findByToken(holdTokenParam)
            if (existingCart != null) {
                // Validate platform ownership
                existingCart.validatePlatformOwnership(platformId)

                // Validate not expired
                if (existingCart.isExpired()) {
                    throw VenuesException.ValidationFailure("Hold has expired")
                }

                // Validate same session
                if (existingCart.sessionId != request.sessionId) {
                    throw VenuesException.ValidationFailure("Cannot add items from different session to existing hold")
                }

                existingCart
            } else {
                throw VenuesException.ResourceNotFound("Hold token not found")
            }
        } else {
            // Create new cart for platform
            val newCart = cartSessionManager.findOrCreateCart(
                token = null,
                sessionId = request.sessionId,
                userId = null,
                isStaffCart = false
            )
            newCart.platformId = platformId
            cartRepository.save(newCart)
        }

        // Extract token to local val for smart cast
        val holdToken = cart.token
        var expiresAt = cart.expiresAt.toString()

        // Add seats (atomic - relies on database unique constraint)
        request.seatIdentifiers?.forEach { seatIdentifier ->
            try {
                val result = cartApi.addSeatToCart(
                    request = AddSeatToCartRequest(
                        sessionId = request.sessionId,
                        code = seatIdentifier
                    ),
                    token = holdToken
                )
                expiresAt = result.expiresAt
            } catch (e: DataIntegrityViolationException) {
                // Seat already in cart - skip (idempotent)
                logger.debug { "Seat $seatIdentifier already in cart $holdToken" }
            }
        }

        // Add GA tickets
        request.gaReservations?.forEach { ga ->
            try {
                val result = cartApi.addGAToCart(
                    request = AddGAToCartRequest(
                        sessionId = request.sessionId,
                        code = ga.levelIdentifier,
                        quantity = ga.quantity
                    ),
                    token = holdToken
                )
                expiresAt = result.expiresAt
            } catch (e: Exception) {
                logger.warn { "Failed to add GA ${ga.levelIdentifier} to cart: ${e.message}" }
                throw e
            }
        }

        // Add tables
        request.tableIdentifiers?.forEach { tableIdentifier ->
            try {
                val result = cartApi.addTableToCart(
                    request = AddTableToCartRequest(
                        sessionId = request.sessionId,
                        code = tableIdentifier
                    ),
                    token = holdToken
                )
                expiresAt = result.expiresAt
            } catch (e: DataIntegrityViolationException) {
                // Table already in cart - skip (idempotent)
                logger.debug { "Table $tableIdentifier already in cart $holdToken" }
            }
        }

        // Get cart summary
        val cartSummary = cartQueryApi.getCartSummary(holdToken)

        logger.info {
            "Platform ${platform.name} held ${cartSummary.seats.size} seats, " +
                    "${cartSummary.gaItems.sumOf { it.quantity }} GA tickets, " +
                    "${cartSummary.tables.size} tables"
        }

        return PlatformHoldResponse(
            holdToken = holdToken,
            expiresAt = expiresAt,
            seats = cartSummary.seats.map { seat ->
                ReservedSeatInfo(
                    seatIdentifier = seat.code,
                    levelName = seat.levelName,
                    seatNumber = seat.number,
                    rowLabel = seat.rowLabel
                )
            },
            gaTickets = cartSummary.gaItems.map { ga ->
                ReservedGAInfo(
                    levelIdentifier = ga.code ?: ga.name,
                    levelName = ga.name,
                    quantity = ga.quantity
                )
            },
            tables = cartSummary.tables.map { table ->
                ReservedTableInfo(
                    tableIdentifier = table.code,
                    tableName = table.number
                )
            },
            totalPrice = cartSummary.totalPrice.toString(),
            currency = cartSummary.currency
        )
    }

    /**
     * Checkout - validate cart and prepare for payment.
     */
    fun checkout(
        platformId: UUID,
        request: PlatformCheckoutRequest,
        idempotencyKey: String?
    ): PlatformCheckoutResponse {
        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = "checkout",
            responseType = PlatformCheckoutResponse::class.java
        ) {
            rateLimitService.enforce(platformId, 100)
            executeCheckout(platformId, request)
        }
    }

    private fun executeCheckout(platformId: UUID, request: PlatformCheckoutRequest): PlatformCheckoutResponse {
        // Find cart and validate ownership
        val cart = cartRepository.findByToken(request.holdToken)
            ?: throw VenuesException.ResourceNotFound("Hold token not found")

        cart.validatePlatformOwnership(platformId)

        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Hold has expired")
        }

        if (cart.isEmpty()) {
            throw VenuesException.ValidationFailure("Hold is empty")
        }

        // Get cart summary
        val cartSummary = cartQueryApi.getCartSummary(request.holdToken)

        return PlatformCheckoutResponse(
            holdToken = request.holdToken,
            totalPrice = cartSummary.totalPrice.toString(),
            currency = cartSummary.currency,
            expiresAt = cartSummary.expiresAt,
            seats = cartSummary.seats.map { seat ->
                ReservedSeatInfo(
                    seatIdentifier = seat.code,
                    levelName = seat.levelName,
                    seatNumber = seat.number,
                    rowLabel = seat.rowLabel
                )
            },
            gaTickets = cartSummary.gaItems.map { ga ->
                ReservedGAInfo(
                    levelIdentifier = ga.code ?: ga.name,
                    levelName = ga.name,
                    quantity = ga.quantity
                )
            },
            tables = cartSummary.tables.map { table ->
                ReservedTableInfo(
                    tableIdentifier = table.code,
                    tableName = table.number
                )
            },
            guestEmail = request.guestEmail,
            guestName = request.guestName
        )
    }

    /**
     * Confirm - create booking and finalize inventory.
     */
    fun confirm(
        platformId: UUID,
        request: PlatformConfirmRequest,
        idempotencyKey: String?
    ): PlatformConfirmResponse {
        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = "confirm",
            responseType = PlatformConfirmResponse::class.java
        ) {
            rateLimitService.enforce(platformId, 100)
            executeConfirm(platformId, request)
        }
    }

    private fun executeConfirm(platformId: UUID, request: PlatformConfirmRequest): PlatformConfirmResponse {
        // Find cart and validate ownership
        val cart = cartRepository.findByToken(request.holdToken)
            ?: throw VenuesException.ResourceNotFound("Hold token not found")

        cart.validatePlatformOwnership(platformId)

        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Hold has expired")
        }

        if (cart.isEmpty()) {
            throw VenuesException.ValidationFailure("Hold is empty")
        }

        // Get cart summary before conversion
        val cartSummary = cartQueryApi.getCartSummary(request.holdToken)

        // Create booking using existing booking API
        val bookingResponse = bookingApi.createBookingFromCart(
            cartToken = request.holdToken,
            platformId = platformId,
            paymentMethod = request.paymentMethod,
            paymentReference = request.paymentReference,
            guestEmail = cartSummary.seats.firstOrNull()?.let { "" } ?: "",
            guestName = "",
            guestPhone = null
        )

        logger.info { "Platform booking confirmed: ${bookingResponse.id}" }

        return PlatformConfirmResponse(
            bookingId = UUID.fromString(bookingResponse.id),
            bookingReference = bookingResponse.id,
            status = bookingResponse.status.name,
            totalAmount = cartSummary.totalPrice.toString(),
            currency = cartSummary.currency,
            confirmedAt = java.time.Instant.now().toString(),
            seats = cartSummary.seats.map { seat ->
                ReservedSeatInfo(
                    seatIdentifier = seat.code,
                    levelName = seat.levelName,
                    seatNumber = seat.number,
                    rowLabel = seat.rowLabel
                )
            },
            gaTickets = cartSummary.gaItems.map { ga ->
                ReservedGAInfo(
                    levelIdentifier = ga.code ?: ga.name,
                    levelName = ga.name,
                    quantity = ga.quantity
                )
            },
            tables = cartSummary.tables.map { table ->
                ReservedTableInfo(
                    tableIdentifier = table.code,
                    tableName = table.number
                )
            }
        )
    }

    /**
     * Release - delete cart and release inventory.
     */
    fun release(
        platformId: UUID,
        request: PlatformReleaseRequest,
        idempotencyKey: String?
    ): PlatformReleaseResponse {
        return idempotencyService.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = "release",
            responseType = PlatformReleaseResponse::class.java
        ) {
            rateLimitService.enforce(platformId, 100)
            executeRelease(platformId, request)
        }
    }

    private fun executeRelease(platformId: UUID, request: PlatformReleaseRequest): PlatformReleaseResponse {
        // Find cart and validate ownership
        val cart = cartRepository.findByToken(request.reservationToken)
            ?: throw VenuesException.ResourceNotFound("Hold token not found")

        cart.validatePlatformOwnership(platformId)

        // Get counts before deletion
        val seatCount = cart.seats.size
        val gaCount = cart.gaItems.sumOf { it.quantity }
        val tableCount = cart.tables.size

        // Clear cart (releases inventory automatically via CartService)
        cartApi.clearCart(request.reservationToken)

        logger.info { "Platform released cart: $seatCount seats, $gaCount GA, $tableCount tables" }

        return PlatformReleaseResponse(
            message = "Inventory released successfully",
            releasedSeats = seatCount,
            releasedGATickets = gaCount,
            releasedTables = tableCount
        )
    }
}
