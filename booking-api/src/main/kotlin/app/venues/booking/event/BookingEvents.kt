package app.venues.booking.event

import java.util.*

/**
 * Events published by the booking module that other modules can listen to.
 */
data class BookingConfirmedEvent(
    val bookingId: UUID,
    val venueId: UUID?,
    val customerEmail: String,
    val customerName: String,
    val locale: String = "en"
)

data class SeatReservedEvent(
    val sessionId: UUID,
    val seatIdentifier: String
)

data class SeatReleasedEvent(
    val sessionId: UUID,
    val seatIdentifier: String,
)

data class GAAvailabilityChangedEvent(
    val sessionId: UUID,
    val levelIdentifier: String,
    val levelName: String,
    val availableTickets: Int,
    val totalCapacity: Int
)

data class TableReservedEvent(
    val sessionId: UUID,
    val tableId: Long,
    val tableName: String,
    val tableCode: String
)

data class TableReleasedEvent(
    val sessionId: UUID,
    val tableId: Long,
    val tableName: String,
    val tableCode: String
)

