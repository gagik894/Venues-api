package app.venues.seating.api.dto

import jakarta.validation.constraints.*
import java.util.*

// =================================================================================
// SEATING CHART (ROOT)
// =================================================================================

data class SeatingChartRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 255, message = "Name must not exceed 255 characters")
    val name: String,

    @field:Min(value = 100, message = "Width must be at least 100")
    val width: Int = 2000,

    @field:Min(value = 100, message = "Height must be at least 100")
    val height: Int = 2000,

    @field:Size(max = 500, message = "Background URL must not exceed 500 characters")
    val backgroundUrl: String? = null
)

data class SeatingChartResponse(
    val id: UUID,
    val venueId: UUID,
    val venueName: String,
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundUrl: String?,
    val totalCapacity: Int,
    val zoneCount: Int,
    val seatCount: Int,
    val createdAt: String,
    val updatedAt: String
)

/**
 * The full hierarchical tree for the frontend renderer.
 */
data class SeatingChartDetailedResponse(
    val id: UUID,
    val venueId: UUID,
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundUrl: String?,
    val rootZones: List<ZoneResponse>, // Hierarchy starts here
    val createdAt: String,
    val updatedAt: String
)

// =================================================================================
// ZONE (CONTAINER)
// =================================================================================

data class ZoneRequest(
    val parentZoneId: Long? = null,

    @field:NotBlank(message = "Zone name is required")
    @field:Size(max = 100)
    val name: String,

    @field:NotBlank(message = "Code prefix is required")
    @field:Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must be uppercase alphanumeric (e.g., ORCH)")
    @field:Size(max = 50)
    val code: String,

    val x: Double = 0.0,
    val y: Double = 0.0,
    val rotation: Double = 0.0,
    val boundaryPath: String? = null,
    val displayColor: String? = null
)

data class ZoneResponse(
    val id: Long,
    val parentZoneId: Long?,
    val name: String,
    val code: String,

    // Rendering
    val x: Double,
    val y: Double,
    val rotation: Double,
    val boundaryPath: String?,
    val displayColor: String?,

    // Inventory Summary
    val seatCount: Int,
    val tableCount: Int,
    val gaCount: Int,

    // Children (Recursive)
    val childZones: List<ZoneResponse>,

    // Content
    val seats: List<SeatResponse>,
    val tables: List<TableResponse>,
    val gaAreas: List<GaAreaResponse>
)

// =================================================================================
// SEAT (INVENTORY)
// =================================================================================

data class SeatRequest(
    @field:NotNull(message = "Zone ID is required")
    val zoneId: Long,

    val tableId: Long? = null,

    @field:NotBlank(message = "Row label is required")
    val rowLabel: String,

    @field:NotBlank(message = "Seat number is required")
    val seatNumber: String,

    val categoryKey: String = "STANDARD",

    val x: Double,
    val y: Double,
    val rotation: Double = 0.0,

    val isAccessible: Boolean = false,
    val isObstructed: Boolean = false
)

data class SeatResponse(
    val id: Long,
    val zoneId: Long,
    val tableId: Long?,
    val code: String, // "ORCH_ROW-A_SEAT-1"
    val rowLabel: String,
    val seatNumber: String,
    val categoryKey: String,
    val x: Double,
    val y: Double,
    val rotation: Double,
    val isAccessible: Boolean,
    val isObstructed: Boolean
)

data class BatchSeatRequest(
    @field:NotNull val zoneId: Long,
    @field:NotEmpty val seats: List<SeatBatchItem>
)

data class SeatBatchItem(
    @field:NotBlank val rowLabel: String,
    @field:NotBlank val seatNumber: String,
    val x: Double,
    val y: Double,
    val rotation: Double = 0.0,
    val categoryKey: String = "STANDARD"
)

// =================================================================================
// TABLE (PHYSICAL OBJECT)
// =================================================================================

data class TableRequest(
    @field:NotNull val zoneId: Long,

    @field:NotBlank val tableNumber: String,
    @field:NotBlank val code: String, // "VIP_T12"

    @field:Min(1) val seatCapacity: Int = 4,

    val shape: String = "ROUND", // Enum: ROUND, SQUARE, RECTANGLE, OVAL

    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double = 0.0
)

data class TableResponse(
    val id: Long,
    val zoneId: Long,
    val code: String,
    val tableNumber: String,
    val seatCapacity: Int,
    val shape: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double
)

// =================================================================================
// GENERAL ADMISSION (AREA)
// =================================================================================

data class GaAreaRequest(
    @field:NotNull val zoneId: Long,

    @field:NotBlank val name: String,
    @field:NotBlank val code: String, // "PIT_A"
    @field:Min(1) val capacity: Int,

    val boundaryPath: String? = null,
    val displayColor: String? = null
)

data class GaAreaResponse(
    val id: Long,
    val zoneId: Long,
    val code: String,
    val name: String,
    val capacity: Int,
    val boundaryPath: String?,
    val displayColor: String?
)