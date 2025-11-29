package app.venues.event.api.dto

import java.util.*

/**
 * Ticket statistics for a session.
 * Used by booking module for sales overview.
 */
data class SessionTicketStatsDto(
    val sessionId: UUID,
    val eventId: UUID,
    val currency: String,
    val ticketsSold: Int,
    val ticketsTotal: Int?
)
