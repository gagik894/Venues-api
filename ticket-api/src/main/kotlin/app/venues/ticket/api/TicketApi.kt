package app.venues.ticket.api

import app.venues.ticket.api.dto.TicketDto
import java.util.*

interface TicketApi {
    fun generateTicketsForBookingItem(
        bookingId: UUID,
        bookingItemId: Long,
        eventSessionId: UUID,
        ticketType: String, // String to avoid dependency on domain enum in API
        seatId: Long? = null,
        gaAreaId: Long? = null,
        tableId: Long? = null,
        quantity: Int,
        qrCode: String? = null
    ): List<TicketDto>

    fun invalidateTicketsForBooking(
        bookingId: UUID,
        staffId: UUID,
        reason: String
    )
}
