package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import app.venues.seating.api.TableBookingMode
import jakarta.persistence.*

/**
 * Configures a "Table" (a type of Level) for a specific EventSession.
 * High-volume child entity (uses AbstractLongEntity).
 *
 * @param session The session this config applies to.
 * @param tableId The `Level.id` (a Long) of the table.
 * @param priceTemplate The `EventPriceTemplate` (a UUID-based entity) for this table.
 */
@Entity
@Table(
    name = "session_table_configs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_session_table_config", columnNames = ["session_id", "table_id"])
    ]
)
class SessionTableConfig(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    @Column(name = "table_id", nullable = false)
    var tableId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_template_id")
    var priceTemplate: EventPriceTemplate? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_mode", length = 20, nullable = false)
    var bookingMode: TableBookingMode = TableBookingMode.FLEXIBLE,

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
            throw IllegalStateException("Session table can't be reserved")
        }
        this._status = ConfigStatus.RESERVED
    }

    /**
     * Sells a reserved or available seat.
     * @throws IllegalStateException if the seat cannot be sold.
     */
    fun sell() {
        if (_status != ConfigStatus.AVAILABLE && _status != ConfigStatus.RESERVED) {
            throw IllegalStateException("Session table can't be sold")
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