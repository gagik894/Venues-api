package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Configures a single Seat for a specific EventSession.
 * High-volume child entity (uses AbstractLongEntity).
 *
 * @param session The session this config applies to.
 * @param seatId The `Seat.id` (a Long) this config is for.
 * @param priceTemplate The `EventPriceTemplate` (a UUID-based entity) for this seat.
 */
@Entity
@Table(
    name = "session_seat_configs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_session_seat_config", columnNames = ["session_id", "seat_id"])
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
    var priceTemplate: EventPriceTemplate? = null,

    ) : AbstractLongEntity() {

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    var status: ConfigStatus = ConfigStatus.AVAILABLE
        protected set

    // --- Public Behaviors ---
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE

    /**
     * Reserves an available seat.
     * @throws IllegalStateException if the seat is not available.
     */
    fun reserve() {
        if (status != ConfigStatus.AVAILABLE) {
            throw IllegalStateException("Seat $seatId is not available to be reserved (status is $status).")
        }
        this.status = ConfigStatus.RESERVED
    }

    /**
     * Sells a reserved or available seat.
     * @throws IllegalStateException if the seat cannot be sold.
     */
    fun sell() {
        if (status != ConfigStatus.AVAILABLE && status != ConfigStatus.RESERVED) {
            throw IllegalStateException("Seat $seatId cannot be sold (status is $status).")
        }
        this.status = ConfigStatus.SOLD
    }

    /**
     * Releases a reserved seat back to available.
     */
    fun release() {
        if (status == ConfigStatus.RESERVED) {
            this.status = ConfigStatus.AVAILABLE
        }
    }
}