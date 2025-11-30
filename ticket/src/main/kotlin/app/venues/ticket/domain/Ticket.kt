package app.venues.ticket.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Ticket entity representing an individual scannable ticket.
 *
 * Domain Model - Minimal design:
 * - Single QR code field (works for venue and platform tickets)
 * - Individual invalidation (separate from booking lifecycle)
 * - Table multi-scan support
 * - Customer info fetched from Booking via JOIN
 */
@Entity
@Table(
    name = "tickets",
    indexes = [
        Index(name = "idx_ticket_qr_code", columnList = "qr_code"),
        Index(name = "idx_ticket_booking_id", columnList = "booking_id"),
        Index(name = "idx_ticket_status", columnList = "status"),
        Index(name = "idx_ticket_event_session", columnList = "event_session_id")
    ]
)
class Ticket(
    @Column(name = "booking_id", nullable = false)
    var bookingId: UUID,

    @Column(name = "booking_item_id", nullable = false)
    var bookingItemId: Long,

    @Column(name = "event_session_id", nullable = false)
    var eventSessionId: UUID,

    @Column(name = "qr_code", unique = true, nullable = false, length = 500)
    var qrCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false, length = 20)
    var ticketType: TicketType,

    @Column(name = "seat_id")
    var seatId: Long? = null,

    @Column(name = "ga_area_id")
    var gaAreaId: Long? = null,

    @Column(name = "table_id")
    var tableId: Long? = null,

    @Column(name = "max_scan_count", nullable = false)
    var maxScanCount: Int = 1,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TicketStatus = TicketStatus.VALID,

    @Column(name = "invalidated_at")
    var invalidatedAt: Instant? = null,

    @Column(name = "invalidated_by_staff_id")
    var invalidatedByStaffId: UUID? = null,

    @Column(name = "invalidation_reason", length = 500)
    var invalidationReason: String? = null

) : AbstractUuidEntity() {

    @OneToMany(mappedBy = "ticket", cascade = [CascadeType.ALL])
    val scans: MutableList<TicketScan> = mutableListOf()

    init {
        require(maxScanCount >= 1) { "maxScanCount must be >= 1" }

        val refCount = listOfNotNull(seatId, gaAreaId, tableId).size
        require(refCount == 1) {
            "Ticket must reference exactly one inventory type"
        }
    }

    /**
     * Check if ticket can be scanned.
     */
    fun canBeScanned(): Boolean {
        return status == TicketStatus.VALID && scans.size < maxScanCount
    }

    /**
     * Scan this ticket, creating a scan record.
     */
    fun scan(scannerSessionId: UUID): TicketScan {
        if (!canBeScanned()) {
            throw IllegalStateException("Ticket cannot be scanned: status=$status, scans=${scans.size}/$maxScanCount")
        }

        val scan = TicketScan(
            ticket = this,
            scannerSessionId = scannerSessionId
        )
        scans.add(scan)

        // Update status if max scans reached
        if (scans.size >= maxScanCount) {
            status = TicketStatus.SCANNED
        }

        return scan
    }

    /**
     * Invalidate this ticket.
     */
    fun invalidate(staffId: UUID, reason: String) {
        require(status != TicketStatus.INVALIDATED) {
            "Ticket already invalidated"
        }

        status = TicketStatus.INVALIDATED
        invalidatedAt = Instant.now()
        invalidatedByStaffId = staffId
        invalidationReason = reason
    }

    fun getScanCount(): Int = scans.size

    fun getRemainingScans(): Int = maxScanCount - scans.size

    @Version
    var version: Long = 0
}
