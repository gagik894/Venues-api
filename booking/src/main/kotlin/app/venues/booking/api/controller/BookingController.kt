package app.venues.booking.api.controller

import app.venues.audit.annotation.Auditable
import app.venues.booking.api.dto.*
import app.venues.booking.service.BookingService
import app.venues.common.model.ApiResponse
import app.venues.shared.persistence.util.PageableMapper
import app.venues.shared.security.util.SecurityUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Controller for booking operations.
 *
 * Provides endpoints for checkout, booking management, and retrieval.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Bookings", description = "Booking management")
class BookingController(
    private val bookingService: BookingService,
    private val securityUtil: SecurityUtil
) {
    private val logger = KotlinLogging.logger {}

    @Value("\${app.security.cookie.secure}")
    private var cookieSecure: Boolean = true

    // ===========================================
    // CHECKOUT (No auth required)
    // ===========================================

    /**
     * Checkout - convert cart to booking.
     */
    @Auditable(action = "BOOKING_CHECKOUT", subjectType = "booking", includeVenueId = false)
    @PostMapping("/checkout")
    @Operation(
        summary = "Checkout",
        description = "Convert cart to booking. No authentication required - provide email. Token can be in body or cookie."
    )
    fun checkout(
        @Valid @RequestBody request: CheckoutRequest,
        @CookieValue(name = "cart_token", required = false) cookieToken: UUID?,
        response: HttpServletResponse
    ): ApiResponse<CheckoutResponse> {
        val effectiveToken = request.token ?: cookieToken
        ?: throw IllegalArgumentException("Cart token is required (body or cookie)")

        val effectiveRequest = request.copy(token = effectiveToken)

        logger.debug { "Processing checkout: token=$effectiveToken" }

        // Try to get user ID if authenticated, but not required
        val userId = try {
            securityUtil.getCurrentUserId()
        } catch (e: Exception) {
            null
        }

        val result = bookingService.checkout(effectiveRequest, userId)

        // Clear cart cookie on successful checkout
        val cookie = Cookie("cart_token", "")
        cookie.path = "/"
        cookie.maxAge = 0
        cookie.secure = cookieSecure
        response.addCookie(cookie)

        return ApiResponse.success(
            data = result,
            message = result.message
        )
    }

    // ===========================================
    // BOOKING RETRIEVAL
    // ===========================================

    /**
     * Get booking by ID (public - anyone with ID can view).
     */
    @GetMapping("/bookings/{id}")
    @Operation(
        summary = "Get booking by ID",
        description = "Retrieve booking details by ID"
    )
    fun getBookingById(@PathVariable id: UUID): ApiResponse<BookingResponse> {
        logger.debug { "Fetching booking: $id" }

        val booking = bookingService.getBookingById(id)

        return ApiResponse.success(
            data = booking,
            message = "Booking retrieved successfully"
        )
    }

    /**
     * Get my bookings (authenticated users only).
     */
    @GetMapping("/bookings/my-bookings")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Get my bookings",
        description = "Get booking history for authenticated user"
    )
    fun getMyBookings(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<BookingResponse>> {
        logger.debug { "Fetching user bookings" }

        val userId = securityUtil.getCurrentUserId()
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val bookings = bookingService.getUserBookings(userId, pageable)

        return ApiResponse.success(
            data = bookings,
            message = "Bookings retrieved successfully"
        )
    }

    /**
     * Get bookings by email (for guests).
     */
    @GetMapping("/bookings/by-email/{email}")
    @Operation(
        summary = "Get bookings by email",
        description = "Retrieve bookings for a guest email address"
    )
    fun getBookingsByEmail(
        @PathVariable email: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<BookingResponse>> {
        logger.debug { "Fetching bookings for email: $email" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val bookings = bookingService.getGuestBookings(email, pageable)

        return ApiResponse.success(
            data = bookings,
            message = "Bookings retrieved successfully"
        )
    }

    // ===========================================
    // BOOKING MANAGEMENT
    // ===========================================

    /**
     * Confirm booking payment.
     */
    @Auditable(action = "BOOKING_CONFIRM", subjectType = "booking", includeVenueId = false)
    @PostMapping("/bookings/{id}/confirm")
    @Operation(
        summary = "Confirm booking",
        description = "Confirm booking after payment"
    )
    fun confirmBooking(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ConfirmBookingRequest
    ): ApiResponse<BookingResponse> {
        logger.debug { "Confirming booking: $id" }

        val userId = try {
            securityUtil.getCurrentUserId()
        } catch (e: Exception) {
            null
        }

        val booking = bookingService.confirmBooking(id, request, userId)

        return ApiResponse.success(
            data = booking,
            message = "Booking confirmed successfully"
        )
    }

    /**
     * Cancel booking.
     */
    @Auditable(action = "BOOKING_CANCEL", subjectType = "booking", includeVenueId = false)
    @PostMapping("/bookings/{id}/cancel")
    @Operation(
        summary = "Cancel booking",
        description = "Cancel a booking"
    )
    fun cancelBooking(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CancelBookingRequest
    ): ApiResponse<BookingResponse> {
        logger.debug { "Cancelling booking: $id" }

        val userId = try {
            securityUtil.getCurrentUserId()
        } catch (e: Exception) {
            null
        }

        val booking = bookingService.cancelBooking(id, request, userId)

        return ApiResponse.success(
            data = booking,
            message = "Booking cancelled successfully"
        )
    }
}

