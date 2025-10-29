package app.venues.event.domain

import app.venues.seating.domain.Level
import jakarta.persistence.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal

/**
 * Session level configuration entity.
 *
 * Maps GA level pricing and availability per session.
 * Allows different GA prices for different showtimes.
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
        Index(name = "idx_session_level_config_template", columnList = "price_template_id"),
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class SessionLevelConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    var level: Level,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_template_id")
    var priceTemplate: EventPriceTemplate? = null,

    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: ConfigStatus = ConfigStatus.AVAILABLE,

    /**
     * Session-specific capacity for GA level.
     * Different sessions can have different capacities for the same area.
     * For example: One concert might allow 1000 standing, another only 500.
     */
    @Column(name = "capacity")
    var capacity: Int? = null,

    /**
     * Denormalized count of sold GA tickets.
     * Updated when bookings are confirmed/cancelled.
     * Avoids expensive COUNT queries on bookings table.
     */
    @Column(name = "sold_count", nullable = false)
    var soldCount: Int = 0,

    ) {
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE

    /**
     * Get available capacity for GA level.
     */
    fun getAvailableCapacity(): Int? {
        return capacity?.let { it - soldCount }
    }
}

