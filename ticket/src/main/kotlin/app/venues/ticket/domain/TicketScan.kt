package app.venues.ticket.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Audit log of ticket scans.
 */
@Entity
@Table(
    name = "ticket_scans",
    indexes = [
        Index(name = "idx_scan_ticket_id", columnList = "ticket_id"),
        Index(name = "idx_scan_session_id", columnList = "scanner_session_id"),
        Index(name = "idx_scan_timestamp", columnList = "scanned_at")
    ]
)
class TicketScan(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    var ticket: Ticket,

    @Column(name = "scanner_session_id", nullable = false)
    var scannerSessionId: UUID,

    @Column(name = "scanned_at", nullable = false)
    var scannedAt: Instant = Instant.now(),

    @Column(name = "scan_location", length = 255)
    var scanLocation: String? = null,

    @Column(name = "device_info", length = 255)
    var deviceInfo: String? = null

) : AbstractLongEntity()
