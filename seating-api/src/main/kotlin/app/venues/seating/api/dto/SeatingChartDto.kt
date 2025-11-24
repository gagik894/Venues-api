package app.venues.seating.api.dto

import java.util.*

/**
 * Basic chart information for cross-module lookups.
 */
data class SeatingChartInfoDto(
    val chartId: UUID,
    val venueId: UUID,
    val chartName: String,
    val width: Int,
    val height: Int
)

/**
 * Complete seating chart structure for frontend canvas rendering.
 * Immutable snapshot of the entire chart hierarchy.
 */
data class SeatingChartStructureDto(
    val chartId: UUID,
    val chartName: String,
    val width: Int,
    val height: Int,
    val zones: List<ZoneDto>,
    val tables: List<TableDto>,
    val seats: List<SeatDto>,
    val gaAreas: List<GaAreaDto>
)

/**
 * Container zone (section/floor) in the chart hierarchy.
 */
data class ZoneDto(
    val id: Long,
    val name: String,
    val code: String,
    val parentZoneId: Long?,
    val x: Double,
    val y: Double,
    val rotation: Double,
    val boundaryPath: String?,
    val displayColor: String?
)

/**
 * Physical table entity with seat capacity.
 */
data class TableDto(
    val id: Long,
    val zoneId: Long,
    val tableNumber: String,
    val code: String,
    val shape: String,
    val seatCapacity: Int,
    val categoryKey: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double
)

/**
 * General admission standing area.
 */
data class GaAreaDto(
    val id: Long,
    val zoneId: Long,
    val name: String,
    val code: String,
    val capacity: Int,
    val categoryKey: String,
    val boundaryPath: String?,
    val displayColor: String?
)

/**
 * Individual seat with rendering position.
 */
data class SeatDto(
    val id: Long,
    val zoneId: Long,
    val tableId: Long?,
    val code: String,
    val rowLabel: String,
    val seatNumber: String,
    val categoryKey: String,
    val isAccessible: Boolean,
    val isObstructed: Boolean,
    val x: Double,
    val y: Double,
    val rotation: Double
)

