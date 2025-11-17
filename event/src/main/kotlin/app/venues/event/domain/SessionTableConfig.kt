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
            throw IllegalStateException("Session table can't be reserved")
        }
        this.status = ConfigStatus.RESERVED
    }

    /**
     * Sells a reserved or available seat.
     * @throws IllegalStateException if the seat cannot be sold.
     */
    fun sell() {
        if (status != ConfigStatus.AVAILABLE && status != ConfigStatus.RESERVED) {
            throw IllegalStateException("Session table can't be sold")
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