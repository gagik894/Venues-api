package app.venues.event.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
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
    var ticketsCount: Int? = null, // Total capacity all tickets

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
    var status: SessionStatus = SessionStatus.ON_SALE
        protected set

    // --- Relationships ---
    @OneToMany(mappedBy = "session", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val priceTemplateOverrides: MutableList<EventSessionPriceOverride> = mutableListOf()

    // --- Public Behaviors ---

    /**
     * Returns the number of remaining tickets for this session.
     *
     * @return Remaining tickets count, or null if unlimited.
     */
    fun getRemainingTickets(): Int? {
        return if (ticketsCount != null) {
            ticketsCount!! - ticketsSold
        } else {
            null
        }
    }

    /**
     * Returns the effective price range for this session.
     * Uses override if set, otherwise falls back to event's price range.
     *
     * @return The price range string, or null.
     */
    fun getEffectivePriceRange(): String? {
        return priceRangeOverride ?: event.priceRange
    }

    /**
     * Checks if tickets are available for purchase.
     *
     * @return true if tickets remain (or unlimited), false if sold out.
     */
    fun hasAvailableTickets(): Boolean {
        return ticketsCount == null || ticketsSold < ticketsCount!!
    }

    /**
     * Checks if the session is currently bookable.
     * Must be ON_SALE, have available tickets, and not yet started.
     *
     * @return true if bookable, false otherwise.
     */
    fun isBookable(): Boolean {
        return status == SessionStatus.ON_SALE && hasAvailableTickets() && startTime.isAfter(Instant.now())
    }

    /**
     * Attempts to sell a specified number of tickets atomically.
     *
     * @param quantity The number of tickets to sell.
     * @return true if sale succeeded, false if insufficient capacity.
     */
    fun sellTickets(quantity: Int): Boolean {
        if (ticketsCount != null && (ticketsSold + quantity) > ticketsCount!!) {
            return false
        }
        this.ticketsSold += quantity
        return true
    }

    /**
     * Returns tickets to inventory (e.g., after refund or cancellation).
     *
     * @param quantity The number of tickets to refund.
     */
    fun refundTickets(quantity: Int) {
        this.ticketsSold = (this.ticketsSold - quantity).coerceAtLeast(0)
    }

    // --- Status Transition Methods ---

    /**
     * Pauses ticket sales for this session.
     * Can only transition from ON_SALE to PAUSED.
     *
     * @throws IllegalStateException if session is not in ON_SALE status.
     */
    fun pauseSales() {
        require(status == SessionStatus.ON_SALE) {
            "Cannot pause sales: current status is $status, must be ON_SALE"
        }
        this.status = SessionStatus.PAUSED
    }

    /**
     * Resumes ticket sales for this session.
     * Can only transition from PAUSED to ON_SALE.
     *
     * @throws IllegalStateException if session is not in PAUSED status.
     */
    fun resumeSales() {
        require(status == SessionStatus.PAUSED) {
            "Cannot resume sales: current status is $status, must be PAUSED"
        }
        this.status = SessionStatus.ON_SALE
    }

    /**
     * Marks the session as sold out.
     * Can transition from ON_SALE or PAUSED to SOLD_OUT.
     *
     * @throws IllegalStateException if session is not in ON_SALE or PAUSED status.
     */
    fun markSoldOut() {
        require(status == SessionStatus.ON_SALE || status == SessionStatus.PAUSED) {
            "Cannot mark sold out: current status is $status, must be ON_SALE or PAUSED"
        }
        this.status = SessionStatus.SOLD_OUT
    }

    /**
     * Closes sales for this session (typically when session has started).
     * Can transition from ON_SALE, PAUSED, or SOLD_OUT to SALES_CLOSED.
     *
     * @throws IllegalStateException if session is already cancelled.
     */
    fun closeSales() {
        require(status != SessionStatus.CANCELLED) {
            "Cannot close sales: session is cancelled"
        }
        require(status != SessionStatus.SALES_CLOSED) {
            "Sales are already closed"
        }
        this.status = SessionStatus.SALES_CLOSED
    }

    /**
     * Cancels the session entirely.
     * Can be called from any status except already CANCELLED.
     * This should trigger refund workflows.
     *
     * @throws IllegalStateException if session is already cancelled.
     */
    fun cancel() {
        require(status != SessionStatus.CANCELLED) {
            "Session is already cancelled"
        }
        this.status = SessionStatus.CANCELLED
    }

    /**
     * Checks if status transition is allowed from current status to target status.
     *
     * @param targetStatus The desired target status.
     * @return true if transition is allowed, false otherwise.
     */
    fun canTransitionTo(targetStatus: SessionStatus): Boolean {
        if (status == targetStatus) return false

        return when (status) {
            SessionStatus.ON_SALE -> targetStatus in setOf(
                SessionStatus.PAUSED,
                SessionStatus.SOLD_OUT,
                SessionStatus.SALES_CLOSED,
                SessionStatus.CANCELLED
            )

            SessionStatus.PAUSED -> targetStatus in setOf(
                SessionStatus.ON_SALE,
                SessionStatus.SOLD_OUT,
                SessionStatus.SALES_CLOSED,
                SessionStatus.CANCELLED
            )

            SessionStatus.SOLD_OUT -> targetStatus in setOf(
                SessionStatus.SALES_CLOSED,
                SessionStatus.CANCELLED
            )

            SessionStatus.SALES_CLOSED -> targetStatus == SessionStatus.CANCELLED
            SessionStatus.CANCELLED -> false
        }
    }

    /**
     * Transitions session to the target status with validation.
     *
     * @param targetStatus The desired status to transition to.
     * @throws IllegalStateException if transition is not allowed.
     */
    fun transitionTo(targetStatus: SessionStatus) {
        require(canTransitionTo(targetStatus)) {
            "Cannot transition from $status to $targetStatus"
        }

        when (targetStatus) {
            SessionStatus.ON_SALE -> resumeSales()
            SessionStatus.PAUSED -> pauseSales()
            SessionStatus.SOLD_OUT -> markSoldOut()
            SessionStatus.SALES_CLOSED -> closeSales()
            SessionStatus.CANCELLED -> cancel()
        }
    }
}
