package app.venues.seating.model

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Composite request for creating or replacing an entire seating chart layout.
 */
data class SeatingChartLayoutRequest(
    @field:Valid
    val chart: SeatingChartRequest,

    @field:Size(min = 1, message = "At least one zone is required")
    val zones: List<@Valid ZoneLayoutRequest>,

    val tables: List<@Valid TableLayoutRequest> = emptyList(),
    val gaAreas: List<@Valid GaAreaLayoutRequest> = emptyList(),
    val seats: List<@Valid SeatLayoutRequest> = emptyList(),
    val landmarks: List<@Valid LandmarkLayoutRequest> = emptyList()
)

data class ZoneLayoutRequest(
    @field:NotBlank(message = "Zone name is required")
    val name: String,

    @field:NotBlank(message = "Zone code is required")
    @field:Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must be uppercase alphanumeric")
    @field:Size(max = 50)
    val code: String,

    val parentCode: String? = null,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val rotation: Double = 0.0,
    val boundaryPath: String? = null,
    val displayColor: String? = null
)

data class TableLayoutRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val zoneCode: String,
    @field:NotBlank val tableNumber: String,
    @field:Min(1)
    val seatCapacity: Int? = null,
    val categoryKey: String = "STANDARD",
    val shape: String = "ROUND",
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double = 0.0
)

data class GaAreaLayoutRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val zoneCode: String,
    @field:NotBlank val name: String,
    @field:Min(1) val capacity: Int,
    val categoryKey: String = "STANDARD",
    val boundaryPath: String? = null,
    val displayColor: String? = null
)

data class SeatLayoutRequest(
    @field:NotBlank val zoneCode: String,
    val tableCode: String? = null,
    @field:NotBlank val rowLabel: String,
    @field:NotBlank val seatNumber: String,
    val categoryKey: String = "STANDARD",
    val isAccessible: Boolean = false,
    val isObstructed: Boolean = false,
    val x: Double,
    val y: Double,
    val rotation: Double = 0.0
)

data class LandmarkLayoutRequest(
    @field:NotBlank val label: String,
    @field:NotBlank val type: String,
    @field:NotBlank val shapeType: String,
    val x: Double,
    val y: Double,
    val width: Double? = null,
    val height: Double? = null,
    val rotation: Double = 0.0,
    val boundaryPath: String? = null,
    val iconKey: String? = null
)
