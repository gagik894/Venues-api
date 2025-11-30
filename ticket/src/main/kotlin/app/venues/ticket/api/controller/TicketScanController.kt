package app.venues.ticket.api.controller

import app.venues.common.model.ApiResponse
import app.venues.ticket.api.ScannerSessionApi
import app.venues.ticket.api.TicketScanApi
import app.venues.ticket.api.dto.ScanRequest
import app.venues.ticket.api.dto.ScanResult
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/tickets/scan")
class TicketScanController(
    private val scanApi: TicketScanApi,
    private val sessionApi: ScannerSessionApi
) {

    @PostMapping
    fun scanTicket(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody request: ScanRequest
    ): ApiResponse<ScanResult> {
        // Validate Bearer token (Scanner Session Token)
        if (!authHeader.startsWith("Bearer ")) {
            return ApiResponse.success(ScanResult.error("Missing Bearer token"), "Invalid authorization header")
        }

        val token = authHeader.substring(7)
        val session = sessionApi.validateSession(token)

        if (session == null) {
            return ApiResponse.success(ScanResult.invalidSession(), "Invalid or expired scanner session")
        }

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
