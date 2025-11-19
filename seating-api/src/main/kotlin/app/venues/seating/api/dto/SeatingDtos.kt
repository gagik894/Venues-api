package app.venues.seating.api.dto

/**
 * Information about a specific Seat (The atomic unit).
 */
data class SeatInfoDto(
    val id: Long,
    val code: String,        // "ORCH_ROW-A_SEAT-1"
    val seatNumber: String,
    val rowLabel: String,
    val zoneId: Long,        // Link to parent Section
    val zoneName: String,
    val categoryKey: String
)

/**
 * Information about a Physical Table.
 */
data class TableInfoDto(
    val id: Long,
    val code: String,        // "VIP_T-12"
    val tableNumber: String,
    val seatCapacity: Int,
    val zoneId: Long,        // Tables live inside Zones
    val zoneName: String
)

/**
 * Information about a Section/Zone (The container).
 */
data class SectionInfoDto(
    val id: Long,
    val code: String,        // "ORCH_C"
    val name: String
)

/**
 * Information about a General Admission Area.
 */
data class GaInfoDto(
    val id: Long,
    val code: String,        // "PIT_A"
    val name: String,
    val capacity: Int,
    val zoneId: Long         // GA areas live inside Zones
)
