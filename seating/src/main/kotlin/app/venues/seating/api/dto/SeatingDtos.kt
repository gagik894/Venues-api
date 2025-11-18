package app.venues.seating.api.dto

import java.util.*

/**
 * Complete seating chart structure for the Frontend Canvas Renderer.
 */
data class SeatingChartStructureDto(
    val chartId: UUID,
    val chartName: String,

    // Coordinate System
    val width: Int,
    val height: Int,

    // The Hierarchy
    val zones: List<ZoneDto>,
    val tables: List<TableDto>,
    val seats: List<SeatDto>,
    val gaAreas: List<GaAreaDto>
)

/**
 * Represents a Container (Section/Floor).
 */
data class ZoneDto(
    val id: Long,
    val name: String,
    val code: String,
    val parentZoneId: Long?,

    // Rendering
    val x: Double,
    val y: Double,
    val rotation: Double,
    val boundaryPath: String?, // SVG path
    val displayColor: String?
)

/**
 * Represents a Physical Table.
 */
data class TableDto(
    val id: Long,
    val zoneId: Long,
    val tableNumber: String,
    val code: String, // e.g. "VIP_T-1"
    val shape: String, // ROUND, SQUARE
    val seatCapacity: Int,

    // Rendering
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double
)

/**
 * Represents a Standing Area (GA).
 */
data class GaAreaDto(
    val id: Long,
    val zoneId: Long,
    val name: String,
    val code: String,
    val capacity: Int,

    // Rendering
    val boundaryPath: String?,
    val displayColor: String?
)

/**
 * Represents an Individual Seat.
 */
data class SeatDto(
    val id: Long,
    val zoneId: Long,
    val tableId: Long?, // Null if not at a table

    val code: String, // "ORCH_ROW-A_SEAT-1"
    val rowLabel: String,
    val seatNumber: String,
    val categoryKey: String,

    val isAccessible: Boolean,
    val isObstructed: Boolean,

    // Rendering
    val x: Double,
    val y: Double,
    val rotation: Double
)