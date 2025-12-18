package app.venues.booking.api.controller

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.booking.api.domain.BookingStatus
import app.venues.booking.api.dto.*
import app.venues.booking.service.*
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
import java.time.LocalDate
import java.util.*

/**
 * Controller for staff booking management.
 *
 * Provides endpoints for venue staff to:
 * - Create direct sales (bypass cart flow)
 * - View all venue bookings
 * - View bookings by event or session
 * - View sales overviews
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
    private val salesOverviewService: SalesOverviewService,
    private val venueSecurityService: VenueSecurityService,
    private val eventStatsService: EventStatsService,
    private val venueReportsService: VenueReportsService
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // DIRECT SALES
    // ===========================================

    /**
     * Create a direct sale (confirmed booking without cart flow).
     * Used when staff sells tickets directly with immediate payment.
     */
    @Auditable(action = "BOOKING_DIRECT_SALE", subjectType = "booking")
    @PostMapping("/direct-sales")
    @Operation(
        summary = "Create direct sale",
        description = "Create a confirmed booking directly, bypassing cart flow. " +
                "Used for in-person sales where payment is received immediately."
    )
    fun createDirectSale(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @Valid @RequestBody request: DirectSaleRequest
    ): ApiResponse<BookingResponse> {
        venueSecurityService.requireVenueSellPermission(staffId, venueId)

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
        venueSecurityService.requireVenueViewPermission(staffId, venueId)

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
        venueSecurityService.requireVenueViewPermission(staffId, venueId)

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
        venueSecurityService.requireVenueViewPermission(staffId, venueId)

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
        venueSecurityService.requireVenueViewPermission(staffId, venueId)

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
    @Auditable(action = "BOOKING_INVALIDATED", subjectType = "booking")
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
        venueSecurityService.requireVenueEditPermission(staffId, venueId)

        logger.info { "Staff $staffId invalidating booking $bookingId for venue $venueId" }
        bookingService.cancelBooking(bookingId)
        return ApiResponse.success(
            data = Unit,
            message = "Booking invalidated successfully"
        )
    }

    // ==================== Sales Overview Endpoints ====================

    /**
     * Get sales overview for a specific session.
     */
    @GetMapping("/sales/sessions/{sessionId}")
    @Operation(
        summary = "Get session sales overview",
        description = "Get ticket counts and revenue for a specific session within the venue"
    )
    fun getSessionSalesOverview(
        @PathVariable venueId: UUID,
        @PathVariable sessionId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<SessionSalesOverview> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)

        logger.info { "Staff $staffId requesting sales overview for session $sessionId in venue $venueId" }
        val overview = salesOverviewService.getSessionSalesOverview(sessionId, venueId)
        return ApiResponse.success(
            data = overview,
            message = "Session sales overview retrieved successfully"
        )
    }

    /**
     * Get rich sales + inventory stats for a session (real-time dashboard).
     */
    @GetMapping("/sales/sessions/{sessionId}/stats")
    @Operation(
        summary = "Get session statistics",
        description = "Returns detailed performance metrics (inventory, revenue, attendance) for a specific session"
    )
    fun getSessionStats(
        @PathVariable venueId: UUID,
        @PathVariable sessionId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<EventStatsResponse> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)
        logger.info { "Staff $staffId requesting stats for session $sessionId in venue $venueId" }
        val stats = eventStatsService.getSessionStats(sessionId, venueId)
        return ApiResponse.success(
            data = stats,
            message = "Session statistics retrieved successfully"
        )
    }

    /**
     * Get aggregated statistics for all sessions of an event.
     */
    @GetMapping("/sales/events/{eventId}/stats")
    @Operation(
        summary = "Get event statistics",
        description = "Returns detailed performance metrics aggregated across every session of an event"
    )
    fun getEventStats(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<EventStatsResponse> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)
        logger.info { "Staff $staffId requesting stats for event $eventId in venue $venueId" }
        val stats = eventStatsService.getEventStats(eventId, venueId)
        return ApiResponse.success(
            data = stats,
            message = "Event statistics retrieved successfully"
        )
    }

    /**
     * Get venue-level reports for a date range of confirmed orders.
     */
    @GetMapping("/reports")
    @Operation(
        summary = "Get venue order reports",
        description = "Returns aggregated orders, revenue, and tickets sold for the venue within the provided date range"
    )
    fun getVenueOrderReports(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate
    ): ApiResponse<ReportsData> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)
        logger.info { "Staff $staffId requesting venue reports for $venueId between $startDate and $endDate" }
        val reports = venueReportsService.getVenueReports(venueId, startDate, endDate)
        return ApiResponse.success(
            data = reports,
            message = "Venue order reports retrieved successfully"
        )
    }

    /**
     * Get sales overview for a specific event (aggregated across all sessions).
     */
    @GetMapping("/sales/events/{eventId}")
    @Operation(
        summary = "Get event sales overview",
        description = "Get aggregated ticket counts and revenue for all sessions of an event within the venue"
    )
    fun getEventSalesOverview(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<EventSalesOverview> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)

        logger.info { "Staff $staffId requesting sales overview for event $eventId in venue $venueId" }
        val overview = salesOverviewService.getEventSalesOverview(eventId, venueId)
        return ApiResponse.success(
            data = overview,
            message = "Event sales overview retrieved successfully"
        )
    }
}
