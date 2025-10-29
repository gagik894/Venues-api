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