package app.venues.ticket.repository

import app.venues.ticket.domain.TicketScan
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface TicketScanRepository : JpaRepository<TicketScan, Long> {
    fun findByTicketId(ticketId: UUID): List<TicketScan>
    fun findByScannerSessionId(scannerSessionId: UUID): List<TicketScan>

    @Query(
        """
        SELECT ts.ticket.id AS ticketId, MIN(ts.scannedAt) AS firstScan
        FROM TicketScan ts
        WHERE ts.ticket.eventSessionId = :sessionId
        GROUP BY ts.ticket.id
        """
    )
    fun findFirstScansBySessionId(sessionId: UUID): List<TicketFirstScanProjection>
}

interface TicketFirstScanProjection {
    fun getTicketId(): UUID
    fun getFirstScan(): Instant?
}
