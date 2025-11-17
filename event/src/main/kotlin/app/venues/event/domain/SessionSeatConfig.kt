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
    private var _status: ConfigStatus = ConfigStatus.AVAILABLE

    val status: ConfigStatus
        get() = _status

    // --- Public Behaviors ---
    fun isAvailable(): Boolean = _status == ConfigStatus.AVAILABLE

    /**
     * Reserves an available seat.
     * @throws IllegalStateException if the seat is not available.
     */
    fun reserve() {
        if (_status != ConfigStatus.AVAILABLE) {
            throw IllegalStateException("Seat $seatId is not available to be reserved (status is $_status).")
        }
        this._status = ConfigStatus.RESERVED
    }

    /**
     * Sells a reserved or available seat.
     * @throws IllegalStateException if the seat cannot be sold.
     */
    fun sell() {
        if (_status != ConfigStatus.AVAILABLE && _status != ConfigStatus.RESERVED) {
            throw IllegalStateException("Seat $seatId cannot be sold (status is $_status).")
        }
        this._status = ConfigStatus.SOLD
    }

    /**
     * Releases a reserved seat back to available.
     */
    fun release() {
        if (_status == ConfigStatus.RESERVED) {
            this._status = ConfigStatus.AVAILABLE
        }
    }
}