package app.venues.seating.api.dto

import java.util.*

/**
 * Static chart structure response for cached static geometry.
 *
 * This response contains only visual/structural data that rarely changes:
 * - Zone hierarchy and boundaries
 * - Seat positions (X, Y, rotation)
 * - Table shapes and positions
 * - Landmarks (Stage, Exit, Bar, etc.)
 *
 * NO dynamic data (status, price) is included. Use SessionInventory API for that.
 * This response is designed to be cached (Cache-Control: public, max-age=86400).
 */
data class StaticChartStructureResponse(
    val chartId: UUID,
    val chartName: String,
    val width: Int,
    val height: Int,
    val backgroundUrl: String?,
    val backgroundTransform: BackgroundTransformDto?,

    /**
     * Root zones containing the recursive hierarchy.
     * Only top-level zones (parentZoneId = null) are included here.
     * Child zones are nested within their parent's [ZoneStructureDto.children].
     */
    val zones: List<ZoneStructureDto>,

    /**
     * Non-bookable visual elements (Stage, Exit, Bar, etc.).
     */
    val landmarks: List<LandmarkDto>
)

/**
 * Recursive zone structure containing children, seats, tables, and GA areas.
 *
 * Zones form a tree hierarchy where each zone can contain:
 * - Child zones (sub-sections)
 * - Seats (bookable seat positions)
 * - Tables (grouped seat containers)
 * - GA areas (standing/general admission areas)
 */
data class ZoneStructureDto(
    val id: Long,
    val name: String,
    val code: String,

    // Rendering attributes
    val x: Double,
    val y: Double,
    val rotation: Double,
    val boundaryPath: String?,
    val displayColor: String?,

    // Recursive children
    val children: List<ZoneStructureDto>,

    // Contained inventory (visual only)
    val seats: List<SeatStructureDto>,
    val tables: List<TableStructureDto>,
    val gaAreas: List<GaAreaStructureDto>
)

/**
 * Seat structure with visual attributes only.
 * NO status or price - those come from SessionInventory API.
 */
data class SeatStructureDto(
    val id: Long,
    val code: String,
    val rowLabel: String,
    val seatNumber: String,
    val categoryKey: String,

    // Visual attributes
    val x: Double,
    val y: Double,
    val rotation: Double,

    // Accessibility metadata
    val isAccessible: Boolean,
    val isObstructed: Boolean,

    // Optional table association
    val tableId: Long?
)

/**
 * Table structure with visual attributes only.
 * NO status or price - those come from SessionInventory API.
 */
data class TableStructureDto(
    val id: Long,
    val code: String,
    val tableNumber: String,
    val seatCapacity: Int,
    val shape: String,

    // Visual attributes
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double
)

/**
 * GA area structure with visual attributes only.
 * NO status, price, or sold count - those come from SessionInventory API.
 */
data class GaAreaStructureDto(
    val id: Long,
    val code: String,
    val name: String,
    val capacity: Int,
    val categoryKey: String,

    // Visual attributes
    val boundaryPath: String?,
    val displayColor: String?
)

/**
 * Non-bookable landmark (Stage, Exit, Bar, etc.).
 * Purely visual elements for user orientation.
 */
data class LandmarkDto(
    val id: Long,
    val label: String,
    val type: String,
    val shapeType: String,

    // Position and size
    val x: Double,
    val y: Double,
    val width: Double?,
    val height: Double?,
    val rotation: Double,

    // Complex shape definition (optional)
    val boundaryPath: String?,
    val iconKey: String?
)

/**
 * Background transform for rendering the chart image.
 */
data class BackgroundTransformDto(
    val x: Double,
    val y: Double,
    val scale: Double,
    val opacity: Double
)
