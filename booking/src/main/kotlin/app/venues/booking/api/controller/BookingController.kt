package app.venues.booking.api.controller

import app.venues.booking.api.dto.*
import app.venues.booking.service.BookingService
import app.venues.common.model.ApiResponse
import app.venues.common.util.PaginationUtil
import app.venues.shared.security.util.SecurityUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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

    // ===========================================
    // CHECKOUT (No auth required)
    // ===========================================

    /**
     * Checkout - convert cart to booking.
     */
    @PostMapping("/checkout")
    @Operation(
        summary = "Checkout",
        description = "Convert cart to booking. No authentication required - provide email."
    )
    fun checkout(@Valid @RequestBody request: CheckoutRequest): ApiResponse<CheckoutResponse> {
        logger.debug { "Processing checkout: token=${request.token}" }

        // Try to get user ID if authenticated, but not required
        val userId = try {
            securityUtil.getCurrentUserId()
        } catch (e: Exception) {
            null
        }

        val result = bookingService.checkout(request, userId)

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
        val pageable = PaginationUtil.createPageable(limit, offset)
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

        val pageable = PaginationUtil.createPageable(limit, offset)
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

