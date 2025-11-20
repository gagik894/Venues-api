package app.venues.event.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Configures a single seat for a specific EventSession.
 * Stores session-specific pricing and availability status for each seat.
 * High-volume child entity (uses AbstractLongEntity for performance).
 *
 * @property session The session this config applies to
 * @property seatId The seat ID from seating module
 * @property priceTemplate The price template for this seat
 */
@Entity
@Table(
    name = "session_seat_configs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_session_seat_config", columnNames = ["session_id", "seat_id"])
    ],
    indexes = [
        Index(name = "idx_session_seat_status", columnList = "session_id, status")
    ]
)
class SessionSeatConfig(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    @Column(name = "seat_id", nullable = false)
    var seatId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_template_id")
    var priceTemplate: EventPriceTemplate? = null
) : AbstractLongEntity() {

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    var status: ConfigStatus = ConfigStatus.AVAILABLE
        protected set

    /**
     * Check if seat is available for purchase.
     */
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE

    /**
     * Reserve an available seat.
     * @throws IllegalStateException if seat is not available
     */
    fun reserve() {
        if (status != ConfigStatus.AVAILABLE) {
            throw IllegalStateException("Seat $seatId cannot be reserved (current status: $status)")
        }
        this.status = ConfigStatus.RESERVED
    }

    /**
     * Sell a reserved or available seat.
     * @throws IllegalStateException if seat cannot be sold
     */
    fun sell() {
        if (status != ConfigStatus.AVAILABLE && status != ConfigStatus.RESERVED) {
            throw IllegalStateException("Seat $seatId cannot be sold (current status: $status)")
        }
        this.status = ConfigStatus.SOLD
    }

    /**
     * Release a reserved seat back to available.
     */
    fun release() {
        if (status == ConfigStatus.RESERVED) {
            this.status = ConfigStatus.AVAILABLE
        }
    }

    /**
     * Block seat from sales (e.g., damaged, removed).
     */
    fun block() {
        this.status = ConfigStatus.BLOCKED
    }

    /**
     * Unblock seat for sales.
     */
    fun unblock() {
        if (status == ConfigStatus.BLOCKED) {
            this.status = ConfigStatus.AVAILABLE
        }
    }
}
