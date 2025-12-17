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
        qrCodes: List<String>? = null
    ): List<TicketDto>

    fun invalidateTicketsForBooking(
        bookingId: UUID,
        staffId: UUID,
        reason: String
    )

    fun invalidateTicketsForBookingItem(
        bookingId: UUID,
        bookingItemId: Long,
        staffId: UUID,
        reason: String
    )

    /**
     * Retrieve all tickets for a booking (for email generation).
     */
    fun getTicketsForBooking(bookingId: UUID): List<TicketDto>
}
