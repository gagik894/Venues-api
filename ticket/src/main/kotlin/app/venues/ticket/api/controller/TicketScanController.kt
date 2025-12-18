package app.venues.ticket.api.controller

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.common.model.ApiResponse
import app.venues.ticket.api.TicketScanApi
import app.venues.ticket.api.dto.ScanRequest
import app.venues.ticket.api.dto.ScanResult
import app.venues.ticket.api.dto.ScannerSessionDto
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tickets/scan")
class TicketScanController(
    private val scanApi: TicketScanApi
) {

    @PostMapping
    @Auditable(action = "TICKET_SCAN", subjectType = "ticket", includeVenueId = false)
    fun scanTicket(
        @AuthenticationPrincipal session: ScannerSessionDto,
        @AuditMetadata("request") @RequestBody request: ScanRequest
    ): ApiResponse<ScanResult> {
        val result = scanApi.scanTicket(
            qrCode = request.qrCode,
            sessionId = session.id,
            deviceInfo = request.deviceInfo,
            scanLocation = request.scanLocation ?: session.sessionName // Fallback to session name/location
        )

        // We always return 200 OK with the result, even if scan failed (business logic failure)
        return ApiResponse.success(result, result.message)
    }
}
