package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

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
        Index(name = "idx_session_seat_config_status", columnList = "status")
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

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: ConfigStatus = ConfigStatus.AVAILABLE,
) : AbstractLongEntity() {
    fun isAvailable(): Boolean = status == ConfigStatus.AVAILABLE
    fun isPriced(): Boolean = priceTemplate != null
}

