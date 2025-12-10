package app.venues.seating.model

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.*

/**
 * REST contract DTOs for seating chart management.
 * These types are used by controllers for HTTP request/response.
 */

// =================================================================================
// SEATING CHART
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
    val backgroundUrl: String? = null,

    /**
     * Optional transform to position/scale a background image.
     */
    val backgroundTransform: BackgroundTransform? = null
)

data class SeatingChartOverviewResponse(
    val id: UUID,
    val name: String,
)

data class SeatingChartResponse(
    val id: UUID,
    val venueId: UUID,
    val venueName: String,
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundUrl: String?,
    val backgroundTransform: BackgroundTransform?,
    val totalCapacity: Int,
    val zoneCount: Int,
    val seatCount: Int,
    val createdAt: String,
    val updatedAt: String
)

data class SeatingChartDetailedResponse(
    val id: UUID,
    val venueId: UUID,
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundUrl: String?,
    val backgroundTransform: BackgroundTransform?,
    val rootZones: List<ZoneResponse>,
    val createdAt: String,
    val updatedAt: String
)

// =================================================================================
// ZONE
// =================================================================================

data class ZoneResponse(
    val id: Long,
    val parentZoneId: Long?,
    val name: String,
    val code: String,
    val x: Double,
    val y: Double,
    val rotation: Double,
    val boundaryPath: String?,
    val displayColor: String?,
    val seatCount: Int,
    val tableCount: Int,
    val gaCount: Int,
    val childZones: List<ZoneResponse>,
    val seats: List<SeatResponse>,
    val tables: List<TableResponse>,
    val gaAreas: List<GaAreaResponse>
)

data class SeatResponse(
    val id: Long,
    val zoneId: Long,
    val tableId: Long?,
    val code: String,
    val rowLabel: String,
    val seatNumber: String,
    val categoryKey: String,
    val x: Double,
    val y: Double,
    val rotation: Double,
    val isAccessible: Boolean,
    val isObstructed: Boolean
)

// =================================================================================
// TABLE
// =================================================================================

data class TableResponse(
    val id: Long,
    val zoneId: Long,
    val code: String,
    val tableNumber: String,
    val seatCapacity: Int,
    val categoryKey: String,
    val shape: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double
)

// =================================================================================
// GENERAL ADMISSION
// =================================================================================

data class GaAreaResponse(
    val id: Long,
    val zoneId: Long,
    val code: String,
    val name: String,
    val capacity: Int,
    val categoryKey: String,
    val boundaryPath: String?,
    val displayColor: String?
)

/**
 * Optional background transform for chart rendering.
 */
data class BackgroundTransform(
    val x: Double,
    val y: Double,
    val scale: Double,
    val opacity: Double
)

