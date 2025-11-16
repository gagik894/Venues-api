package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * Price template override for a specific event session.
 *
 * Allows sessions to have different pricing than the event's default.
 * Example: Matinee shows cheaper than evening shows.
 */
@Entity
@Table(
    name = "event_session_price_overrides",
    indexes = [
        Index(name = "idx_session_price_override_session_id", columnList = "session_id")
    ]
)
class EventSessionPriceOverride(
    /**
     * The session this override applies to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    /**
     * Name of the ticket tier being overridden
     */
    @Column(name = "template_name", nullable = false, length = 100)
    var templateName: String,

    /**
     * Overridden price for this session
     */
    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,
) : AbstractLongEntity()

