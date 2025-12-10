package app.venues.seating.model

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * Request to clone an existing seating chart into a new chart.
 * Only the name is required; other attributes are inherited unless overridden.
 */
data class CloneSeatingChartRequest(
    @field:NotBlank(message = "Name is required for cloned chart")
    val name: String,
    val backgroundUrl: String? = null
)

/**
 * Batch visual-only updates. No business keys or structural relations can change here.
 */
data class SeatingChartVisualUpdateRequest(
    val zones: List<@Valid ZoneVisualUpdate> = emptyList(),
    val seats: List<@Valid SeatVisualUpdate> = emptyList(),
    val tables: List<@Valid TableVisualUpdate> = emptyList(),
    val gaAreas: List<@Valid GaAreaVisualUpdate> = emptyList(),
    val landmarks: LandmarkUpdates? = null
)

data class ZoneVisualUpdate(
    val id: Long,
    val name: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val rotation: Double? = null,
    val boundaryPath: String? = null,
    val displayColor: String? = null
)

data class SeatVisualUpdate(
    val id: Long,
    val x: Double? = null,
    val y: Double? = null,
    val rotation: Double? = null,
    val isAccessible: Boolean? = null,
    val isObstructed: Boolean? = null,
    val categoryKey: String? = null
)

data class TableVisualUpdate(
    val id: Long,
    val x: Double? = null,
    val y: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    val rotation: Double? = null,
    val shape: String? = null,
    val categoryKey: String? = null
)

data class GaAreaVisualUpdate(
    val id: Long,
    @field:Min(1, message = "Capacity must be positive")
    val capacity: Int? = null,
    val boundaryPath: String? = null,
    val displayColor: String? = null,
    val categoryKey: String? = null
)

/**
 * Landmarks are purely visual, so we support create/update/delete in one payload.
 */
data class LandmarkUpdates(
    val upserts: List<@Valid LandmarkUpsert> = emptyList(),
    val deleteIds: List<Long> = emptyList()
)

data class LandmarkUpsert(
    val id: Long? = null, // null means create
    @field:NotBlank(message = "Label is required")
    val label: String,
    @field:NotBlank(message = "Type is required")
    val type: String,
    @field:NotBlank(message = "Shape type is required")
    val shapeType: String,
    val x: Double,
    val y: Double,
    val width: Double? = null,
    val height: Double? = null,
    val rotation: Double = 0.0,
    val boundaryPath: String? = null,
    val iconKey: String? = null
)

