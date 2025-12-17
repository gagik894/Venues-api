package app.venues.booking.api.dto

import app.venues.shared.money.MoneyAmount
import java.util.*

/**
 * Sales overview for a single session.
 */
data class SessionSalesOverview(
    val sessionId: UUID,
    val eventId: UUID,
    val ticketsSold: Int,
    val ticketsTotal: Int?,
    val totalRevenue: MoneyAmount,
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
    val totalRevenue: MoneyAmount,
    val confirmedBookingsCount: Int,
    val currency: String,
    val sessions: List<SessionSalesOverview>
)
