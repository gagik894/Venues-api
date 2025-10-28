package app.venues.event.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant

/**
 * Event Session entity representing a specific time slot for an event.
 *
 * An event can have multiple sessions (e.g., multiple showtimes for a theater performance).
 * Each session can have its own pricing and ticket capacity.
 */
@Entity
@Table(
    name = "event_sessions",
    indexes = [
        Index(name = "idx_event_session_event_id", columnList = "event_id"),
        Index(name = "idx_event_session_start_time", columnList = "start_time"),
        Index(name = "idx_event_session_status", columnList = "status")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class EventSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The parent event
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,

    /**
     * Session start time
     */
    @Column(name = "start_time", nullable = false)
    var startTime: Instant,

    /**
     * Session end time
     */
    @Column(name = "end_time", nullable = false)
    var endTime: Instant,

    /**
     * Total number of tickets available for this session
     */
    @Column(name = "tickets_count")
    var ticketsCount: Int? = null,

    /**
     * Number of tickets already sold
     */
    @Column(name = "tickets_sold", nullable = false)
    var ticketsSold: Int = 0,

    /**
     * Status of this specific session
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: EventStatus = EventStatus.UPCOMING,

    /**
     * Price override for this session (if different from event's default)
     */
    @Column(name = "price_override", precision = 10, scale = 2)
    var priceOverride: BigDecimal? = null,

    /**
     * Price range override for display
     */
    @Column(name = "price_range_override", length = 100)
    var priceRangeOverride: String? = null,

    /**
     * Price template overrides specific to this session
     */
    @OneToMany(mappedBy = "session", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var priceTemplateOverrides: MutableList<EventSessionPriceOverride> = mutableListOf(),

    // ===========================================
    // Audit Fields
    // ===========================================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    /**
     * Get the effective price for this session
     */
    fun getEffectivePrice(): BigDecimal? {
        return priceOverride
    }

    /**
     * Get the effective price range for this session
     */
    fun getEffectivePriceRange(): String? {
        return priceRangeOverride ?: event.priceRange
    }

    /**
     * Check if session has available tickets
     */
    fun hasAvailableTickets(): Boolean {
        return ticketsCount == null || ticketsSold < ticketsCount!!
    }

    /**
     * Get remaining tickets count
     */
    fun getRemainingTickets(): Int? {
        return ticketsCount?.let { it - ticketsSold }
    }

    /**
     * Check if session is bookable
     */
    fun isBookable(): Boolean {
        return status == EventStatus.UPCOMING && hasAvailableTickets() && startTime.isAfter(Instant.now())
    }
}

