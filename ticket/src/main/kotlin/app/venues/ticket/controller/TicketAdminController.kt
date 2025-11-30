package app.venues.ticket.controller

import app.venues.ticket.api.dto.CreateSessionRequest
import app.venues.ticket.api.dto.ScannerSessionDto
import app.venues.ticket.api.dto.TicketDto
import app.venues.ticket.domain.Ticket
import app.venues.ticket.repository.TicketRepository
import app.venues.ticket.service.ScannerSessionService
import app.venues.ticket.service.TicketGenerationService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/admin/tickets")
@PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
class TicketAdminController(
    private val ticketRepository: TicketRepository,
    private val ticketGenerationService: TicketGenerationService,
    private val scannerSessionService: ScannerSessionService
) {

    @GetMapping("/booking/{bookingId}")
    fun getTicketsForBooking(@PathVariable bookingId: UUID): List<TicketDto> {
        return ticketRepository.findByBookingId(bookingId).map { it.toDto() }
    }

    @PostMapping("/invalidate")
    fun invalidateTickets(
        @RequestParam bookingId: UUID,
        @RequestParam staffId: UUID, // In real app, get from SecurityContext
        @RequestParam reason: String
    ): ResponseEntity<Void> {
        ticketGenerationService.invalidateTicketsForBooking(bookingId, staffId, reason)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/invalidate-item")
    fun invalidateTicketsForItem(
        @RequestParam bookingId: UUID,
        @RequestParam bookingItemId: Long,
        @RequestParam staffId: UUID,
        @RequestParam reason: String
    ): ResponseEntity<Void> {
        ticketGenerationService.invalidateTicketsForBookingItem(bookingId, bookingItemId, staffId, reason)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/sessions")
    fun createScannerSession(@RequestBody request: CreateSessionRequest): ScannerSessionDto {
        // In real app, get staffId from SecurityContext
        val staffId = UUID.fromString("00000000-0000-0000-0000-000000000000")

        return scannerSessionService.createSession(
            eventId = request.eventId,
            sessionName = request.sessionName,
            validUntil = request.validUntil,
            scanLocation = request.scanLocation,
            venueId = request.venueId,
            staffId = staffId
        )
    }

    private fun Ticket.toDto() = TicketDto(
        id = id,
        ticketNumber = null, // Not used in entity
        qrCode = qrCode,
        ticketType = ticketType.name,
        status = status.name,
        maxScanCount = maxScanCount,
        scanCount = getScanCount(),
        remainingScans = getRemainingScans()
    )
}
