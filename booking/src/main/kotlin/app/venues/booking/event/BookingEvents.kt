package app.venues.booking.event

import java.util.*

/**
 * Events published by the booking module that other modules can listen to.
 */

/**
 * Event fired when a seat is added to cart (reserved)
 */
data class SeatReservedEvent(
    val sessionId: Long,
    val seatIdentifier: String,
    val reservationToken: UUID,
    val expiresAt: String
)

/**
 * Event fired when a seat is removed from cart or reservation expires
 */
data class SeatReleasedEvent(
    val sessionId: Long,
    val seatIdentifier: String,
    val levelName: String
)

/**
 * Event fired when GA tickets availability changes
 */
data class GAAvailabilityChangedEvent(
    val sessionId: Long,
    val levelIdentifier: String,
    val levelName: String,
    val availableTickets: Int,
    val totalCapacity: Int
)

