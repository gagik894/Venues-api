package app.venues.ticket.domain

enum class TicketStatus {
    /**
     * Ticket is valid and can be scanned.
     */
    VALID,

    /**
     * All allowed scans have been used.
     */
    SCANNED,

    /**
     * Ticket has been manually invalidated (refund, cancellation, etc.).
     */
    INVALIDATED,

    /**
     * Event has passed, ticket expired.
     */
    EXPIRED
}
