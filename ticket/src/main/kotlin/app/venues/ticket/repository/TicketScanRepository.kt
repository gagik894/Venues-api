package app.venues.ticket.repository

import app.venues.ticket.domain.TicketScan
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TicketScanRepository : JpaRepository<TicketScan, Long> {
    fun findByTicketId(ticketId: UUID): List<TicketScan>
    fun findByScannerSessionId(scannerSessionId: UUID): List<TicketScan>
}
