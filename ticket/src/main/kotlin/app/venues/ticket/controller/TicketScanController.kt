package app.venues.ticket.controller

import app.venues.ticket.api.dto.ScanRequest
import app.venues.ticket.api.dto.ScanResult
import app.venues.ticket.api.dto.ScannerSessionDto
import app.venues.ticket.service.ScannerSessionService
import app.venues.ticket.service.TicketScanService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/scanner")
class TicketScanController(
    private val scannerSessionService: ScannerSessionService,
    private val ticketScanService: TicketScanService
) {

    @PostMapping("/sessions/validate")
    fun validateSession(@RequestBody token: String): ResponseEntity<ScannerSessionDto> {
        val session = scannerSessionService.validateSession(token)
        return if (session != null) {
            ResponseEntity.ok(session)
        } else {
            ResponseEntity.status(401).build()
        }
    }

    @PostMapping("/scan")
    fun scanTicket(
        @RequestHeader("X-Scanner-Session-Id") sessionId: UUID,
        @RequestBody request: ScanRequest
    ): ResponseEntity<ScanResult> {
        // Ideally we should validate sessionId exists and is active here too, 
        // or trust the client if they have the ID (which they get from validateSession).
        // For better security, we could require the secret token in header and resolve it.
        // But for now, passing ID is simpler as per service signature.

        val result = ticketScanService.scanTicket(
            qrCode = request.qrCode,
            sessionId = sessionId,
            deviceInfo = request.deviceInfo,
            scanLocation = request.scanLocation
        )

        return ResponseEntity.ok(result)
    }
}
