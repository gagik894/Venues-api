package app.venues.event.domain

import app.venues.seating.api.TableBookingMode
import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Configures a table for a specific EventSession.
 * Tables are groups of seats that can be booked as a complete unit.
 * High-volume child entity (uses AbstractLongEntity for performance).
 *
 * @property session The session this config applies to
 * @property tableId The table ID from seating module
 * @property priceTemplate The price template for this table (whole table price)
 * @property bookingMode How the table can be booked (TABLE_ONLY, SEATS_ONLY, FLEXIBLE)
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
    var bookingMode: TableBookingMode = TableBookingMode.FLEXIBLE
) : AbstractLongEntity() {

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    var status: ConfigStatus = ConfigStatus.AVAILABLE
        protected set

    /**
     * Check if table is available for booking.
     */
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE

    /**
     * Reserve an available table.
     * @throws IllegalStateException if table is not available
     */
    fun reserve() {
        if (status != ConfigStatus.AVAILABLE) {
            throw IllegalStateException("Table $tableId cannot be reserved (current status: $status)")
        }
        this.status = ConfigStatus.RESERVED
    }

    /**
     * Sell a reserved or available table.
     * @throws IllegalStateException if table cannot be sold
     */
    fun sell() {
        if (status != ConfigStatus.AVAILABLE && status != ConfigStatus.RESERVED) {
            throw IllegalStateException("Table $tableId cannot be sold (current status: $status)")
        }
        this.status = ConfigStatus.SOLD
    }

    /**
     * Release a reserved table back to available.
     */
    fun release() {
        if (status == ConfigStatus.RESERVED) {
            this.status = ConfigStatus.AVAILABLE
        }
    }

    /**
     * Block table from sales.
     */
    fun block() {
        this.status = ConfigStatus.BLOCKED
    }

    /**
     * Unblock table for sales.
     */
    fun unblock() {
        if (status == ConfigStatus.BLOCKED) {
            this.status = ConfigStatus.AVAILABLE
        }
    }

    /**
     * Check if table can only be booked as a complete unit.
     */
    fun isTableOnly(): Boolean = bookingMode == TableBookingMode.TABLE_ONLY

    /**
     * Check if individual seats can be booked.
     */
    fun allowsSeatBooking(): Boolean =
        bookingMode == TableBookingMode.SEATS_ONLY || bookingMode == TableBookingMode.FLEXIBLE
}
