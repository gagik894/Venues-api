package app.venues.booking.api.dto

import java.math.BigDecimal
import java.util.*

/**
 * Sales overview for a single session.
 */
data class SessionSalesOverview(
    val sessionId: UUID,
    val eventId: UUID,
    val ticketsSold: Int,
    val ticketsTotal: Int?,
    val totalRevenue: BigDecimal,
    val confirmedBookingsCount: Int,
    val currency: String
)

/**
 * Sales overview for an entire event (aggregated across all sessions).
 */
data class EventSalesOverview(
    val eventId: UUID,
    val ticketsSold: Int,
    val ticketsTotal: Int?,
    val totalRevenue: BigDecimal,
    val confirmedBookingsCount: Int,
    val currency: String,
    val sessions: List<SessionSalesOverview>
)
