package app.venues.booking.domain

/**
 * Booking status enum.
 */
enum class BookingStatus {
    /**
     * Awaiting payment confirmation
     */
    PENDING,

    /**
     * Payment confirmed, booking finalized
     */
    CONFIRMED,

    /**
     * Booking was cancelled
     */
    CANCELLED,

    /**
     * Payment refunded
     */
    REFUNDED
}

/**
 * Session seat/level configuration status.
 */
enum class ConfigStatus {
    /**
     * Available for booking
     */
    AVAILABLE,

    /**
     * Closed for this specific session
     */
    CLOSED,

    /**
     * Blocked (obstructed view, maintenance, etc.)
     * Only applicable to seats
     */
    BLOCKED
}

