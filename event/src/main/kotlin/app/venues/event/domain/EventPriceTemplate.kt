package app.venues.event.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * A "root" definition for a ticket price tier (e.g., "VIP", "Standard").
 * This is referenced by sessions, so it must have a stable UUID.
 *
 * @param event The event this template belongs to.
 * @param templateName The display name (e.g., "VIP").
 * @param price The price for this tier.
 * @param color An optional color code (e.g., "#FFD700") for UI representation.
 */
@Entity
@Table(name = "event_price_templates")
class EventPriceTemplate(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,

    @Column(name = "template_name", nullable = false, length = 100)
    var templateName: String,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Column(name = "color", length = 7)
    var color: String? = null,
) : AbstractUuidEntity()
