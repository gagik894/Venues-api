package app.venues.event.dto

/**
 * DTO for session seat availability statistics.
 * Used to efficiently retrieve aggregated seat counts from the database.
 *
 * @param totalSeats Total number of seats configured for the session
 * @param availableSeats Number of seats with AVAILABLE status
 * @param reservedSeats Number of seats with RESERVED status
 */
data class AvailabilityStatsDto(
    val totalSeats: Long,
    val availableSeats: Long,
    val reservedSeats: Long
)

