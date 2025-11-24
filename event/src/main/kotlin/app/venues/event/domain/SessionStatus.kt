package app.venues.event.domain

/**
 * Enumeration of possible event session statuses.
 */
enum class SessionStatus {
    /**
     * Tickets are available for purchase.
     * BUTTON: Active ("Buy Tickets").
     */
    ON_SALE,

    /**
     * Sales halted manually by Host (e.g. to fix a pricing error).
     * BUTTON: Disabled ("Sales Paused").
     */
    PAUSED,

    /**
     * Manual override to stop sales even if inventory remains (or calculated).
     * BUTTON: Disabled ("Sold Out").
     */
    SOLD_OUT,

    /**
     * Sales stopped because the event has started (or door time passed).
     * BUTTON: Disabled ("Sales Closed").
     */
    SALES_CLOSED,

    /**
     * The specific performance will not happen.
     * BUTTON: Disabled ("Cancelled" - distinct red badge).
     * LOGIC: Triggers refund workflows.
     */
    CANCELLED
}
