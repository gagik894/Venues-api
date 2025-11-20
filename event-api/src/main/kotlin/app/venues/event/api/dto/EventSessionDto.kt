package app.venues.event.api.dto

import java.time.Instant
import java.util.*

data class EventSessionDto(
    val sessionId: UUID,
    val eventId: UUID,
    val venueId: UUID,
    val eventTitle: String,
    val eventDescription: String?,
    val currency: String,
    val startTime: Instant,
    val endTime: Instant
)
