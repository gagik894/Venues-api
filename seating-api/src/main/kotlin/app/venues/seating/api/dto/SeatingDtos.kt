package app.venues.seating.api.dto

/**
 * Seat information DTO for cross-module communication.
 */
data class SeatInfoDto(
    val id: Long,
    val seatIdentifier: String,
    val seatNumber: String?,
    val rowLabel: String?,
    val levelId: Long,
    val levelName: String
)

/**
 * Level information DTO for cross-module communication.
 *
 * This DTO is "smart" and carries the results of business logic
 * from the Seating module (e.g., booking rules).
 */
data class LevelInfoDto(
    val id: Long,
    val levelName: String,
    val levelIdentifier: String?,
    val capacity: Int?, // For GA levels
    val isGeneralAdmission: Boolean
)

/**
 * Complete seating chart structure for bulk loading.
 * Contains all information needed to render and process a seating chart.
 */
data class SeatingChartStructureDto(
    val chartId: Long,
    val chartName: String,
    val levels: List<LevelDto>,
    val seats: List<SeatDto>
)

/**
 * Level DTO with hierarchy information.
 *
 * This DTO is "smart" and carries the results of business logic
 * from the Seating module (e.g., booking rules).
 */
data class LevelDto(
    val id: Long,
    val levelName: String,
    val levelIdentifier: String?,
    val parentLevelId: Long?,
    val capacity: Int?,
    val positionX: Double?,
    val positionY: Double?,
    val isTable: Boolean? = null,
)

/**
 * Seat DTO with position and level association.
 */
data class SeatDto(
    val id: Long,
    val seatIdentifier: String,
    val seatNumber: String?,
    val rowLabel: String?,
    val levelId: Long,
    val positionX: Double?,
    val positionY: Double?,
    val seatType: String?
)

