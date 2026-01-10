package app.venues.platform.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.CartApi
import app.venues.booking.api.CartQueryApi
import app.venues.booking.api.CartValidationApi
import app.venues.booking.api.dto.*
import app.venues.common.exception.VenuesException
import app.venues.platform.api.dto.*
import app.venues.platform.repository.PlatformRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import app.venues.booking.api.dto.PlatformGAReservation as BookingPlatformGAReservation

/**
 * Platform booking service implementing secure /hold, /checkout, /confirm flow.
 *
 * Security features:
 * - Cart-platform binding (audit fix CRIT-01, CRIT-02)
 * - Atomic operations to prevent race conditions (audit fix CRIT-03)
 * - Transaction boundaries for consistency (audit fix STATE-01)
 * - Pessimistic locking for booking confirmation (audit fix HIGH-01)
 *
 * Idempotency:
 * - Handled declaratively via @Idempotent annotation on controller methods
 * - No manual idempotency logic in service layer (separation of concerns)
 */
@Service
@Transactional
class PlatformBookingService(
    private val platformRepository: PlatformRepository,
    private val cartApi: CartApi,
    private val cartQueryApi: CartQueryApi,
    private val cartValidationApi: CartValidationApi,
    private val bookingApi: BookingApi,
    private val rateLimitService: PlatformRateLimitService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Hold inventory - create cart and reserve seats/GA/tables.
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun hold(
        platformId: UUID,
        request: PlatformHoldRequest
    ): PlatformHoldResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)
        return executeHold(platformId, request)
    }

    /**
     * Simple hold using booking batch hold with optional TTL override.
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun holdSimple(
        platformId: UUID,
        request: PlatformHoldRequest
    ): PlatformHoldResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)

        val summary = cartApi.holdBatch(
            PlatformHoldBatchRequest(
                sessionId = request.sessionId,
                holdToken = request.holdToken,
                seatIdentifiers = request.seatIdentifiers,
                gaReservations = request.gaReservations?.map {
                    BookingPlatformGAReservation(
                        levelIdentifier = it.levelIdentifier,
                        quantity = it.quantity
                    )
                },
                tableIdentifiers = request.tableIdentifiers,
                platformId = platformId,
                ttlSeconds = request.ttlSeconds
            )
        )

        return PlatformHoldResponse(
            holdToken = summary.token,
            expiresAt = summary.expiresAt,
            seats = summary.seats.map { seat ->
                ReservedSeatInfo(
                    seatIdentifier = seat.code,
                    levelName = seat.levelName,
                    seatNumber = seat.number,
                    rowLabel = seat.rowLabel
                )
            },
            gaTickets = summary.gaItems.map { ga ->
                ReservedGAInfo(
                    levelIdentifier = ga.code ?: ga.name,
                    levelName = ga.name,
                    quantity = ga.quantity
                )
            },
            tables = summary.tables.map { table ->
                ReservedTableInfo(
                    tableIdentifier = table.code,
                    tableName = table.number
                )
            },
            totalPrice = summary.totalPrice.toString(),
            currency = summary.currency
        )
    }

    private fun executeHold(platformId: UUID, request: PlatformHoldRequest): PlatformHoldResponse {
        val holdTokenParam = request.holdToken
        if (holdTokenParam != null) {
            validateExistingCart(platformId, holdTokenParam, request.sessionId)
        }

        var holdToken = holdTokenParam
        var expiresAt = ""

        // Add seats (atomic - relies on database unique constraint)
        request.seatIdentifiers?.forEach { seatIdentifier ->
            try {
                val result = cartApi.addSeatToCart(
                    request = AddSeatToCartRequest(
                        sessionId = request.sessionId,
                        code = seatIdentifier
                    ),
                    token = holdToken,
                    platformId = platformId
                )
                expiresAt = result.expiresAt
                holdToken = result.token
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
                    token = holdToken,
                    platformId = platformId
                )
                expiresAt = result.expiresAt
                holdToken = result.token
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
                    token = holdToken,
                    platformId = platformId
                )
                expiresAt = result.expiresAt
                holdToken = result.token
            } catch (e: DataIntegrityViolationException) {
                // Table already in cart - skip (idempotent)
                logger.debug { "Table $tableIdentifier already in cart $holdToken" }
            }
        }

        val finalToken = holdToken ?: throw VenuesException.ValidationFailure("No items provided for hold")

        // Get cart summary
        val cartSummary = cartQueryApi.getCartSummary(finalToken)
        if (expiresAt.isBlank()) {
            expiresAt = cartSummary.expiresAt
        }

        logger.info {
            "Platform $platformId held ${cartSummary.seats.size} seats, " +
                    "${cartSummary.gaItems.sumOf { it.quantity }} GA tickets, " +
                    "${cartSummary.tables.size} tables"
        }

        return PlatformHoldResponse(
            holdToken = finalToken,
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
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun checkout(
        platformId: UUID,
        request: PlatformCheckoutRequest
    ): PlatformCheckoutResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)
        return executeCheckout(platformId, request)
    }

    private fun executeCheckout(platformId: UUID, request: PlatformCheckoutRequest): PlatformCheckoutResponse {
        validateExistingCart(platformId, request.holdToken, null)

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
            guestEmail = request.guestEmail ?: "",
            guestName = request.guestName ?: ""
        )
    }

    /**
     * Confirm - create booking and finalize inventory.
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun confirm(
        platformId: UUID,
        request: PlatformConfirmRequest
    ): PlatformConfirmResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)
        return executeConfirm(platformId, request)
    }

    private fun executeConfirm(platformId: UUID, request: PlatformConfirmRequest): PlatformConfirmResponse {
        validateExistingCart(platformId, request.holdToken, null)

        // Get cart summary before conversion
        val cartSummary = cartQueryApi.getCartSummary(request.holdToken)

        // Create booking using existing booking API
        val bookingResponse = bookingApi.createBookingFromCart(
            cartToken = request.holdToken,
            platformId = platformId,
            paymentReference = request.paymentReference,
            guestEmail = request.guestEmail,
            guestName = request.guestName,
            guestPhone = request.guestPhone
        )

        logger.info { "Platform booking confirmed: ${bookingResponse.id}" }

        return PlatformConfirmResponse(
            bookingId = UUID.fromString(bookingResponse.id),
            bookingReference = bookingResponse.id,
            status = bookingResponse.status.name,
            totalAmount = cartSummary.totalPrice.toString(),
            currency = cartSummary.currency,
            confirmedAt = bookingResponse.confirmedAt ?: java.time.Instant.now().toString(),
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
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun release(
        platformId: UUID,
        request: PlatformReleaseRequest
    ): PlatformReleaseResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)
        return executeRelease(platformId, request)
    }

    private fun executeRelease(platformId: UUID, request: PlatformReleaseRequest): PlatformReleaseResponse {
        // Clear cart (releases inventory automatically via CartService). Allow expired cleanup.
        val summary = cartApi.clearCart(request.reservationToken, platformId = platformId)

        logger.info { "Platform released cart: ${summary.seats.size} seats, ${summary.gaItems.sumOf { it.quantity }} GA, ${summary.tables.size} tables" }

        return PlatformReleaseResponse(
            message = "Inventory released successfully",
            releasedSeats = summary.seats.size,
            releasedGATickets = summary.gaItems.sumOf { it.quantity },
            releasedTables = summary.tables.size
        )
    }

    /**
     * Direct platform booking (confirmed) skipping cart.
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun directBooking(
        platformId: UUID,
        request: DirectSaleRequest
    ): BookingResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)

        return bookingApi.createPlatformDirectBooking(
            request = request,
            platformId = platformId,
            guestEmail = request.customerEmail,
            guestName = request.customerName,
            guestPhone = request.customerPhone,
            confirmBooking = true
        )
    }

    /**
     * Easy Mode: Reserve - Create PENDING booking directly.
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun reserveSimple(
        platformId: UUID,
        request: PlatformEasyReserveRequest
    ): BookingResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)

        val directSaleRequest = request.toDirectSaleRequest()
        return bookingApi.createPlatformDirectBooking(
            request = directSaleRequest,
            platformId = platformId,
            confirmBooking = false // Create PENDING booking
        )
    }

    /**
     * Easy Mode: Confirm - Mark PENDING booking as SOLD.
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun confirmSimple(
        platformId: UUID,
        bookingId: UUID
    ): BookingResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)

        // Validate booking belongs to platform
        val booking = bookingApi.getBookingById(bookingId)
        if (booking.platformId != platformId) {
            throw VenuesException.AuthorizationFailure("Booking does not belong to this platform")
        }

        bookingApi.confirmPlatformBooking(bookingId)

        // Return updated booking
        return bookingApi.getBookingById(bookingId)
    }

    /**
     * Easy Mode: Release - Cancel PENDING booking.
     *
     * Idempotency handled by @Idempotent annotation on controller.
     */
    fun releaseSimple(
        platformId: UUID,
        bookingId: UUID
    ): BookingResponse {
        val platform = loadPlatform(platformId)
        rateLimitService.enforce(platformId, platform.rateLimit)

        // Validate booking belongs to platform
        val booking = bookingApi.getBookingById(bookingId)
        if (booking.platformId != platformId) {
            throw VenuesException.AuthorizationFailure("Booking does not belong to this platform")
        }

        bookingApi.cancelBooking(bookingId)

        // Return updated booking
        return bookingApi.getBookingById(bookingId)
    }

    private fun loadPlatform(platformId: UUID) =
        platformRepository.findById(platformId).orElseThrow {
            VenuesException.ResourceNotFound("Platform not found")
        }.also {
            if (!it.isActive()) {
                throw VenuesException.ValidationFailure("Platform is not active")
            }
        }

    /**
     * Validate that the cart exists, belongs to the platform, is not expired,
     * and (if provided) matches the expected session.
     */
    private fun validateExistingCart(platformId: UUID, cartToken: UUID, expectedSessionId: UUID?) {
        cartValidationApi.validateCartForPlatform(
            token = cartToken,
            platformId = platformId,
            expectedSessionId = expectedSessionId
        )
    }
}

