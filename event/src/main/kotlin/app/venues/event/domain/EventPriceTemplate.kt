package app.venues.event.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * Price template for an event defining different ticket types/tiers.
 *
 * Examples: VIP, Standard, Balcony, Student, etc.
 * Each template has a name, color (for UI display), and price.
 */
@Entity
@Table(
    name = "event_price_templates",
    indexes = [
        Index(name = "idx_price_template_event_id", columnList = "event_id")
    ]
)
class EventPriceTemplate(
    /**
     * The event this price template belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,

    /**
     * Name of the ticket tier (e.g., "VIP", "Standard", "Student")
     */
    @Column(name = "template_name", nullable = false, length = 100)
    var templateName: String,

    /**
     * Color code for UI display (hex: #FF5733)
     */
    @Column(length = 7)
    var color: String? = null,

    /**
     * Price for this ticket tier
     */
    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    /**
     * Display order for sorting
     */
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
) : AbstractUuidEntity()

