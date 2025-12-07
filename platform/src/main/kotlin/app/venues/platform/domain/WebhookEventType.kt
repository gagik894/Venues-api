package app.venues.platform.domain

/**
 * Webhook event types
 */
enum class WebhookEventType {
    /**
     * Seat was reserved (added to cart)
     */
    SEAT_RESERVED,

    /**
     * Seat reservation was released (removed from cart or expired)
     */
    SEAT_RELEASED,

    /**
     * Seat was booked (payment confirmed)
     */
    SEAT_BOOKED,

    /**
     * Booking was cancelled (seat becomes available again)
     */
    BOOKING_CANCELLED,

    /**
     * GA tickets availability changed
     */
    GA_AVAILABILITY_CHANGED,

    /**
     * Table was reserved (added to cart)
     */
    TABLE_RESERVED,

    /**
     * Table reservation was released
     */
    TABLE_RELEASED,

    /**
     * Session seat configuration updated
     */
    SESSION_CONFIG_UPDATED
}
