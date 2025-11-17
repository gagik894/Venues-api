package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * Overrides the price of an `EventPriceTemplate` for a specific `EventSession`.
 * This is a child entity of EventSession.
 *
 * @param session The session this override applies to.
 * @param templateName The name of the template being overridden (e.g., "VIP").
 * @param price The new price for this session only.
 */
@Entity
@Table(name = "event_session_price_overrides")
class EventSessionPriceOverride(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    @Column(name = "template_name", nullable = false, length = 100)
    var templateName: String,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    ) : AbstractLongEntity()