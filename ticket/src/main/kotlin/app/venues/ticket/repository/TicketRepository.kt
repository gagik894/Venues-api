package app.venues.ticket.repository

import app.venues.ticket.domain.Ticket
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TicketRepository : JpaRepository<Ticket, UUID> {
    fun findByQrCode(qrCode: String): Ticket?
    fun findByBookingId(bookingId: UUID): List<Ticket>
    fun findByBookingItemId(bookingItemId: Long): List<Ticket>

    fun countByEventSessionId(eventSessionId: UUID): Long
}
