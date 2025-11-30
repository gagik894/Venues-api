package app.venues.ticket.api.controller

import app.venues.common.model.ApiResponse
import app.venues.ticket.api.ScannerSessionApi
import app.venues.ticket.api.dto.CreateSessionRequest
import app.venues.ticket.api.dto.ScannerSessionDto
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/admin/scanner-sessions")
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
class ScannerSessionController(
    private val sessionApi: ScannerSessionApi
) {

    @PostMapping
    fun createSession(
        @RequestAttribute("userId") staffId: UUID, // Assuming staff ID is in "userId" attribute
        @RequestBody request: CreateSessionRequest
    ): ApiResponse<ScannerSessionDto> {
        val session = sessionApi.createSession(
            eventId = request.eventId,
            sessionName = request.sessionName,
            validUntil = request.validUntil,
            scanLocation = request.scanLocation,
            venueId = request.venueId,
            staffId = staffId
        )
        return ApiResponse.success(session)
    }
}
