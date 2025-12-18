package app.venues.ticket.api.controller

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.common.model.ApiResponse
import app.venues.ticket.api.ScannerSessionApi
import app.venues.ticket.api.dto.CreateSessionRequest
import app.venues.ticket.api.dto.ScannerSessionDto
import app.venues.ticket.api.dto.TicketResponse
import app.venues.ticket.api.mapper.TicketMapper
import app.venues.ticket.repository.TicketRepository
import app.venues.ticket.service.TicketGenerationService
import app.venues.venue.api.service.VenueSecurityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/staff/venues/{venueId}/tickets")
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
@Tag(name = "Staff Tickets", description = "Ticket management for venue staff")
class StaffTicketController(
    private val ticketRepository: TicketRepository,
    private val ticketGenerationService: TicketGenerationService,
    private val scannerSessionApi: ScannerSessionApi,
    private val venueSecurityService: VenueSecurityService,
    private val ticketMapper: TicketMapper
) {
    private val logger = KotlinLogging.logger {}

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get tickets for booking")
    fun getTicketsForBooking(
        @PathVariable venueId: UUID,
        @PathVariable bookingId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<List<TicketResponse>> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)
        logger.debug { "Staff $staffId fetching tickets for booking $bookingId in venue $venueId" }

        val tickets = ticketRepository.findByBookingId(bookingId).map { ticketMapper.toResponse(it) }
        return ApiResponse.success(tickets, "Tickets retrieved successfully")
    }

    @PostMapping("/invalidate")
    @Operation(summary = "Invalidate all tickets for booking")
    @Auditable(action = "TICKET_INVALIDATE_BOOKING", subjectType = "booking")
    fun invalidateTickets(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("bookingId") @RequestParam bookingId: UUID,
        @AuditMetadata("reason") @RequestParam reason: String
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        logger.info { "Staff $staffId invalidating tickets for booking $bookingId in venue $venueId" }

        ticketGenerationService.invalidateTicketsForBooking(bookingId, staffId, reason)
        return ApiResponse.success(Unit, "Tickets invalidated successfully")
    }

    @PostMapping("/invalidate-item")
    @Operation(summary = "Invalidate tickets for specific booking item")
    @Auditable(action = "TICKET_INVALIDATE_ITEM", subjectType = "booking_item")
    fun invalidateTicketsForItem(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("bookingId") @RequestParam bookingId: UUID,
        @AuditMetadata("bookingItemId") @RequestParam bookingItemId: Long,
        @AuditMetadata("reason") @RequestParam reason: String
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        logger.info { "Staff $staffId invalidating tickets for item $bookingItemId in booking $bookingId" }

        ticketGenerationService.invalidateTicketsForBookingItem(bookingId, bookingItemId, staffId, reason)
        return ApiResponse.success(Unit, "Tickets invalidated successfully")
    }

    @GetMapping("/sessions")
    @Operation(summary = "Get scanner sessions for venue")
    fun getScannerSessions(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<List<ScannerSessionDto>> {
        venueSecurityService.requireVenueScanPermission(staffId, venueId)
        logger.debug { "Staff $staffId fetching scanner sessions for venue $venueId" }
        val sessions = scannerSessionApi.getSessionsForVenue(venueId)
        return ApiResponse.success(sessions, "Scanner sessions retrieved successfully")
    }


    @PostMapping("/sessions")
    @Operation(summary = "Create scanner session")
    @Auditable(action = "SCANNER_SESSION_CREATE", subjectType = "scanner_session")
    fun createScannerSession(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @RequestBody request: CreateSessionRequest
    ): ApiResponse<ScannerSessionDto> {
        venueSecurityService.requireVenueScanPermission(staffId, venueId)
        logger.info { "Staff $staffId creating scanner session for venue $venueId" }

        // Ensure the session is created for the correct venue
        if (request.venueId != venueId) {
            throw IllegalArgumentException("Venue ID mismatch")
        }

        val session = scannerSessionApi.createSession(
            eventId = request.eventId,
            sessionName = request.sessionName,
            validUntil = request.validUntil,
            scanLocation = request.scanLocation,
            venueId = venueId,
            staffId = staffId
        )
        return ApiResponse.success(session, "Scanner session created successfully")
    }
}
