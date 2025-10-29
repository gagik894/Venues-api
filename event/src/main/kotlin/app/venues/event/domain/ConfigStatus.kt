package app.venues.event.domain

/**
 * Configuration status for session seat/level configs.
 *
 * Represents availability and booking state for seats in a specific session.
 */
enum class ConfigStatus {
    /**
     * Available for booking
     */
    AVAILABLE,

    /**
     * Temporarily reserved (in cart, not yet confirmed)
     */
    RESERVED,

    /**
     * Sold (booking confirmed)
     */
    SOLD,

    /**
     * Closed by venue (maintenance, VIP hold, etc.)
     */
    CLOSED,

    /**
     * Blocked (not available for this session)
     */
    BLOCKED
}

