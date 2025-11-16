package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Session GA level configuration.
 *
 * Assigns price templates and tracks capacity per session.
 * Prices are read from the template and snapshotted to the cart.
 */
@Entity
@Table(
    name = "session_level_configs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_session_level_config", columnNames = ["session_id", "level_id"])
    ],
    indexes = [
        Index(name = "idx_session_level_config_session", columnList = "session_id"),
        Index(name = "idx_session_level_config_level", columnList = "level_id"),
        Index(name = "idx_session_level_config_status", columnList = "status")
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

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: ConfigStatus = ConfigStatus.AVAILABLE,

    @Column(name = "capacity")
    var capacity: Int? = null,

    @Column(name = "sold_count", nullable = false)
    var soldCount: Int = 0,
) : AbstractLongEntity() {
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE
    fun isPriced(): Boolean = priceTemplate != null
    fun getAvailableCapacity(): Int? = capacity?.let { it - soldCount }
}