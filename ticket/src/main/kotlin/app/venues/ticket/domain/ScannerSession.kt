package app.venues.ticket.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

/**
 * Scanner session for QR-code based authentication.
 *
 * Admin generates session → QR code → Volunteers scan to activate app.
 */
@Entity
@Table(
    name = "scanner_sessions",
    indexes = [
        Index(name = "idx_scanner_session_token", columnList = "secret_token"),
        Index(name = "idx_scanner_session_event", columnList = "event_id")
    ]
)
class ScannerSession(
    @Column(name = "event_id", nullable = false)
    var eventId: UUID,

    @Column(name = "session_name", nullable = false, length = 255)
    var sessionName: String,

    @Column(name = "secret_token", unique = true, nullable = false, length = 100)
    var secretToken: String,

    @Column(name = "valid_until", nullable = false)
    var validUntil: Instant,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "scan_location", length = 255)
    var scanLocation: String? = null,

    @Column(name = "venue_id", nullable = false)
    var venueId: UUID,

    @Column(name = "created_by_staff_id", nullable = false)
    var createdByStaffId: UUID

) : AbstractUuidEntity() {

    /**
     * Check if session is currently valid.
     */
    fun isValid(): Boolean {
        return active && Instant.now().isBefore(validUntil)
    }

    /**
     * Deactivate this session (cannot be used anymore).
     */
    fun deactivate() {
        active = false
    }
}
