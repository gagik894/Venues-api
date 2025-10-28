package app.venues.booking.domain

import app.venues.event.domain.EventPriceTemplate
import app.venues.event.domain.EventSession
import app.venues.seating.domain.Level
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant

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
        Index(name = "idx_session_level_config_status", columnList = "status")
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

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE
}

