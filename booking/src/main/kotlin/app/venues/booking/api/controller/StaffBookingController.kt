package app.venues.booking.api.controller

import app.venues.booking.api.domain.BookingStatus
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.DirectSaleRequest
import app.venues.booking.service.BookingService
import app.venues.booking.service.DirectSalesService
import app.venues.common.model.ApiResponse
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.service.VenueSecurityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Controller for staff booking management.
 *
 * Provides endpoints for venue staff to:
 * - Create direct sales (bypass cart flow)
 * - View all venue bookings
 * - View bookings by event or session
 */
@RestController
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/v1/staff/venues/{venueId}/bookings")
@Tag(
    name = "Staff Bookings",
    description = "Booking management for venue staff"
)
class StaffBookingController(
    private val directSalesService: DirectSalesService,
    private val bookingService: BookingService,
    private val venueSecurityService: VenueSecurityService
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // DIRECT SALES
    // ===========================================

    /**
     * Create a direct sale (confirmed booking without cart flow).
     * Used when staff sells tickets directly with immediate payment.
     */
    @PostMapping("/direct-sales")
    @Operation(
        summary = "Create direct sale",
        description = "Create a confirmed booking directly, bypassing cart flow. " +
                "Used for in-person sales where payment is received immediately."
    )
    fun createDirectSale(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: DirectSaleRequest
    ): ApiResponse<BookingResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.info { "Staff $staffId creating direct sale for venue $venueId" }

        val booking = directSalesService.createDirectSale(request, venueId, staffId)

        return ApiResponse.success(
            data = booking,
            message = "Direct sale completed successfully"
        )
    }

    // ===========================================
    // BOOKING QUERIES
    // ===========================================

    /**
     * Get all bookings for the venue with optional filters.
     */
    @GetMapping
    @Operation(
        summary = "Get venue bookings",
        description = "Get all bookings for the venue with pagination and optional status filter"
    )
    fun getVenueBookings(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) status: BookingStatus?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<BookingResponse>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Staff $staffId fetching bookings for venue $venueId, status=$status" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val bookings = bookingService.getVenueBookings(venueId, status, pageable)

        return ApiResponse.success(
            data = bookings,
            message = "Bookings retrieved successfully"
        )
    }

    /**
     * Get bookings for a specific session.
     */
    @GetMapping("/by-session/{sessionId}")
    @Operation(
        summary = "Get session bookings",
        description = "Get all bookings for a specific session within the venue"
    )
    fun getSessionBookings(
        @PathVariable venueId: UUID,
        @PathVariable sessionId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<BookingResponse>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Staff $staffId fetching bookings for session $sessionId in venue $venueId" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val bookings = bookingService.getSessionBookings(sessionId, venueId, pageable)

        return ApiResponse.success(
            data = bookings,
            message = "Session bookings retrieved successfully"
        )
    }

    /**
     * Get bookings for a specific event (across all sessions).
     */
    @GetMapping("/by-event/{eventId}")
    @Operation(
        summary = "Get event bookings",
        description = "Get all bookings for a specific event (across all sessions) within the venue"
    )
    fun getEventBookings(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<BookingResponse>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Staff $staffId fetching bookings for event $eventId in venue $venueId" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val bookings = bookingService.getEventBookings(eventId, venueId, pageable)

        return ApiResponse.success(
            data = bookings,
            message = "Event bookings retrieved successfully"
        )
    }

    /**
     * Get a specific booking by ID.
     */
    @GetMapping("/{bookingId}")
    @Operation(
        summary = "Get booking details",
        description = "Get details of a specific booking within the venue"
    )
    fun getBookingById(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<BookingResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Staff $staffId fetching booking $bookingId for venue $venueId" }

        val booking = bookingService.getBookingById(bookingId)

        return ApiResponse.success(
            data = booking,
            message = "Booking retrieved successfully"
        )
    }

    /**
     * Invalidate a booking by ID.
     */
    @DeleteMapping("/{bookingId}")
    @Operation(
        summary = "Invalidate booking",
        description = "Invalidate (cancel) a specific booking within the venue"
    )
    fun invalidateBooking(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.info { "Staff $staffId invalidating booking $bookingId for venue $venueId" }
        bookingService.cancelBooking(bookingId)
        return ApiResponse.success(
            data = Unit,
            message = "Booking invalidated successfully"
        )
    }
}
