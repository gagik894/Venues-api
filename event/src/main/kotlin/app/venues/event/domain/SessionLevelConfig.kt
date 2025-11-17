package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Configures a GA Level for a specific EventSession.
 * High-volume child entity (uses AbstractLongEntity).
 *
 * @param session The session this config applies to.
 * @param levelId The `Level.id` (a Long) this config is for.
 * @param priceTemplate The `EventPriceTemplate` (a UUID-based entity) for this level.
 * @param capacity The maximum capacity for this level in the session (null = unlimited).
 */
@Entity
@Table(
    name = "session_level_configs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_session_level_config", columnNames = ["session_id", "level_id"])
    ]
)
class SessionLevelConfig(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    @Column(name = "level_id", nullable = false)
    var levelId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_template_id")
    var priceTemplate: EventPriceTemplate? = null,

    @Column(name = "capacity")
    var capacity: Int? = null,

) : AbstractLongEntity() {

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    private var _status: ConfigStatus = ConfigStatus.AVAILABLE

    val status: ConfigStatus
        get() = _status

    @Column(name = "sold_count", nullable = false)
    @Access(AccessType.FIELD)
    private var _soldCount: Int = 0

    val soldCount: Int
        get() = _soldCount

    fun isAvailable(): Boolean = _status == ConfigStatus.AVAILABLE

    fun getAvailableCapacity(): Int? = capacity?.let { it - _soldCount }

    /**
     * Attempts to sell a specified number of tickets.
     * @return true if the sale was successful, false if not enough capacity.
     */
    fun sell(quantity: Int): Boolean {
        if (capacity != null && (_soldCount + quantity) > capacity!!) {
            return false // Not enough capacity
        }
        this._soldCount += quantity
        return true
    }

    /**
     * Puts tickets back into inventory.
     */
    fun refund(quantity: Int) {
        this._soldCount = (this._soldCount - quantity).coerceAtLeast(0)
    }
}