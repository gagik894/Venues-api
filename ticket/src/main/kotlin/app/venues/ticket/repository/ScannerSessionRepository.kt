package app.venues.ticket.repository

import app.venues.ticket.domain.ScannerSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ScannerSessionRepository : JpaRepository<ScannerSession, UUID> {
    fun findBySecretToken(secretToken: String): ScannerSession?
    fun findByEventId(eventId: UUID): List<ScannerSession>
    fun findByVenueId(venueId: UUID): List<ScannerSession>
}
