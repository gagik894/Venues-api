package app.venues.event.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Session seat configuration.
 *
 * Assigns price templates and tracks availability per session.
 * Prices are read from the template and snapshotted to the cart.
 */
@Entity
@Table(
    name = "session_seat_configs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_session_seat_config", columnNames = ["session_id", "seat_id"])
    ],
    indexes = [
        Index(name = "idx_session_seat_config_session", columnList = "session_id"),
        Index(name = "idx_session_seat_config_seat", columnList = "seat_id"),
        Index(name = "idx_session_seat_config_template", columnList = "price_template_id"),
        Index(name = "idx_session_seat_config_status", columnList = "status")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class SessionSeatConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    @Column(name = "seat_id", nullable = false)
    var seatId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_template_id")
    var priceTemplate: EventPriceTemplate? = null,

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
    fun isPriced(): Boolean = priceTemplate != null
}

