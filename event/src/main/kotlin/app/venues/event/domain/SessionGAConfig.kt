package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Configures a General Admission (GA) area for a specific event session.
 *
 * GA areas are standing/general admission zones from the seating module
 * with capacity tracking and sold count management.
 *
 * High-volume child entity (uses AbstractLongEntity for performance).
 *
 * @property session The session this config applies to
 * @property gaAreaId The GA area ID from seating module
 * @property priceTemplate The price template for this GA area
 * @property capacity The maximum capacity for this GA area in this session
 */
@Entity
@Table(
    name = "session_level_configs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_session_level_config", columnNames = ["session_id", "ga_area_id"])
    ]
)
class SessionGAConfig(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    @Column(name = "ga_area_id", nullable = false)
    var gaAreaId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_template_id")
    var priceTemplate: EventPriceTemplate? = null,

    @Column(name = "capacity")
    var capacity: Int? = null
) : AbstractLongEntity() {

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    var status: ConfigStatus = ConfigStatus.AVAILABLE
        protected set

    @Column(name = "sold_count", nullable = false)
    @Access(AccessType.FIELD)
    var soldCount: Int = 0
        protected set

    /**
     * Check if GA area is available for purchase.
     */
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE

    /**
     * Get remaining capacity for GA area.
     */
    fun getAvailableCapacity(): Int? = capacity?.let { it - soldCount }

    /**
     * Sell tickets for GA area.
     * @param quantity Number of tickets to sell
     * @return true if successful, false if not enough capacity
     * @throws IllegalStateException if GA area is not available
     */
    fun sell(quantity: Int): Boolean {
        if (status != ConfigStatus.AVAILABLE) {
            throw IllegalStateException("GA area is not available (status: $status)")
        }

        if (capacity != null && (soldCount + quantity) > capacity!!) {
            return false
        }

        this.soldCount += quantity
        return true
    }

    /**
     * Refund tickets for GA area.
     * @param quantity Number of tickets to refund
     */
    fun refund(quantity: Int) {
        this.soldCount = (this.soldCount - quantity).coerceAtLeast(0)
    }

    /**
     * Block GA area from sales.
     */
    fun block() {
        this.status = ConfigStatus.BLOCKED
    }

    /**
     * Unblock GA area for sales.
     */
    fun unblock() {
        this.status = ConfigStatus.AVAILABLE
    }
}