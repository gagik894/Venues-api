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

data class SeatClosedEvent(
    val sessionId: UUID,
    val seatIdentifier: String
)

data class SeatOpenedEvent(
    val sessionId: UUID,
    val seatIdentifier: String
)

data class TableClosedEvent(
    val sessionId: UUID,
    val tableIdentifier: String
)

data class TableOpenedEvent(
    val sessionId: UUID,
    val tableIdentifier: String
)

