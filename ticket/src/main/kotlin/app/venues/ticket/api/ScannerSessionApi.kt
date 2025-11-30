package app.venues.ticket.api

import app.venues.ticket.api.dto.ScannerSessionDto
import java.time.Instant
import java.util.*

interface ScannerSessionApi {
    fun createSession(
        eventId: UUID,
        sessionName: String,
        validUntil: Instant,
        scanLocation: String?,
        venueId: UUID,
        staffId: UUID
    ): ScannerSessionDto

    fun validateSession(token: String): ScannerSessionDto?
}
