package app.venues.booking.event

import java.util.*

/**
 * Events published by the booking module that other modules can listen to.
 */

/**
 * Event fired when a seat is added to cart (reserved)
 */
data class SeatReservedEvent(
    val sessionId: UUID,
    val seatIdentifier: String
)

/**
 * Event fired when a seat is removed from cart or reservation expires
 */
data class SeatReleasedEvent(
    val sessionId: UUID,
    val seatIdentifier: String,
)

/**
 * Event fired when GA tickets availability changes
 */
data class GAAvailabilityChangedEvent(
    val sessionId: UUID,
    val levelIdentifier: String,
    val levelName: String,
    val availableTickets: Int,
    val totalCapacity: Int
)

/**
 * Event fired when a table is reserved
 */
data class TableReservedEvent(
    val sessionId: UUID,
    val tableId: Long,
    val tableName: String
)

/**
 * Event fired when a table is released
 */
data class TableReleasedEvent(
    val sessionId: UUID,
    val tableId: Long,
    val tableName: String
)

