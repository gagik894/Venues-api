package app.venues.event.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/**
 * A specific, bookable time slot for an Event.
 * This is a "root" entity, as it's the primary object referenced by Bookings.
 *
 * @param event The parent event.
 * @param startTime The exact start time of the session.
 * @param endTime The exact end time of the session.
 * @param ticketsCount The total number of tickets available for this session (null means unlimited).
 * @param priceOverride An optional override for the base event price.
 * @param priceRangeOverride An optional override for the event's price range display.
 */
@Entity
@Table(name = "event_sessions")
class EventSession(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,

    @Column(name = "start_time", nullable = false)
    var startTime: Instant,

    @Column(name = "end_time", nullable = false)
    var endTime: Instant,

    @Column(name = "tickets_count")
    var ticketsCount: Int? = null, // Total capacity (for GA)

    @Column(name = "price_override", precision = 10, scale = 2)
    var priceOverride: BigDecimal? = null,

    @Column(name = "price_range_override", length = 100)
    var priceRangeOverride: String? = null,

    ) : AbstractUuidEntity() {

    // --- Internal State (Encapsulated) ---
    @Column(name = "tickets_sold", nullable = false)
    @Access(AccessType.FIELD)
    var ticketsSold: Int = 0
        protected set

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    var status: EventStatus = EventStatus.UPCOMING
        protected set

    // --- Relationships ---
    @OneToMany(mappedBy = "session", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val priceTemplateOverrides: MutableList<EventSessionPriceOverride> = mutableListOf()

    // --- Public Behaviors ---

    fun getRemainingTickets(): Int? {
        return if (ticketsCount != null) {
            ticketsCount!! - ticketsSold
        } else {
            null
        }
    }

    fun getEffectivePriceRange(): String? {
        return priceRangeOverride ?: event.priceRange
    }

    fun hasAvailableTickets(): Boolean {
        // A null ticketsCount means unlimited (or at least, not tracked here)
        return ticketsCount == null || ticketsSold < ticketsCount!!
    }

    fun isBookable(): Boolean {
        return status == EventStatus.UPCOMING && hasAvailableTickets() && startTime.isAfter(Instant.now())
    }

    /**
     * Attempts to sell a specified number of tickets.
     * @return true if the sale was successful, false if not enough capacity.
     */
    fun sellTickets(quantity: Int): Boolean {
        if (ticketsCount != null && (ticketsSold + quantity) > ticketsCount!!) {
            return false // Not enough capacity
        }
        this.ticketsSold += quantity
        return true
    }

    /**
     * Puts tickets back into inventory.
     */
    fun refundTickets(quantity: Int) {
        this.ticketsSold = (this.ticketsSold - quantity).coerceAtLeast(0)
    }
}