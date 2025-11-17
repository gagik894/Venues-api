package app.venues.seating.api.dto

import jakarta.validation.constraints.*
import java.util.*

// ===========================================
// SEATING CHART DTOs
// ===========================================

/**
 * Request DTO for creating/updating seating charts.
 */
data class SeatingChartRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 255, message = "Name must not exceed 255 characters")
    val name: String,

    @field:Min(value = 1, message = "Seat indicator size must be at least 1")
    val seatIndicatorSize: Int = 1,

    @field:Min(value = 1, message = "Level indicator size must be at least 1")
    val levelIndicatorSize: Int = 1,

    @field:Size(max = 500, message = "Background URL must not exceed 500 characters")
    val backgroundUrl: String? = null
)

/**
 * Response DTO for seating chart.
 */
data class SeatingChartResponse(
    val id: UUID,
    val venueId: UUID,
    val venueName: String,
    val name: String,
    val seatIndicatorSize: Int,
    val levelIndicatorSize: Int,
    val backgroundUrl: String?,
    val totalCapacity: Int,
    val levelCount: Int,
    val seatCount: Int,
    val createdAt: String,
    val lastModifiedAt: String
)

/**
 * Detailed seating chart response with levels and seats.
 */
data class SeatingChartDetailedResponse(
    val id: UUID,
    val venueId: UUID,
    val venueName: String,
    val name: String,
    val seatIndicatorSize: Int,
    val levelIndicatorSize: Int,
    val backgroundUrl: String?,
    val totalCapacity: Int,
    val levels: List<LevelResponse>,
    val createdAt: String,
    val lastModifiedAt: String
)

// ===========================================
// LEVEL DTOs
// ===========================================

/**
 * Request DTO for creating/updating levels.
 */
data class LevelRequest(
    val parentLevelId: Long? = null,

    @field:NotBlank(message = "Level name is required")
    @field:Size(max = 255, message = "Level name must not exceed 255 characters")
    val levelName: String,

    @field:Size(max = 50, message = "Level identifier must not exceed 50 characters")
    val levelIdentifier: String? = null,

    val positionX: Double? = null,

    val positionY: Double? = null,

    @field:Min(value = 0, message = "Capacity must be non-negative")
    val capacity: Int? = null
)

/**
 * Response DTO for level.
 */
data class LevelResponse(
    val id: Long,
    val parentLevelId: Long?,
    val levelName: String,
    val levelIdentifier: String?,
    val positionX: Double?,
    val positionY: Double?,
    val capacity: Int?,
    val isGeneralAdmission: Boolean,
    val isSeatedSection: Boolean,
    val seatCount: Int,
    val childLevels: List<LevelResponse>?,
    val createdAt: String
)

// ===========================================
// SEAT DTOs
// ===========================================

/**
 * Request DTO for creating/updating seats.
 */
data class SeatRequest(
    @field:NotNull(message = "Level ID is required")
    val levelId: Long,

    @field:NotBlank(message = "Seat identifier is required")
    @field:Size(max = 50, message = "Seat identifier must not exceed 50 characters")
    val seatIdentifier: String,

    @field:Size(max = 50, message = "Seat number must not exceed 50 characters")
    val seatNumber: String? = null,

    @field:Size(max = 50, message = "Row label must not exceed 50 characters")
    val rowLabel: String? = null,

    val positionX: Double? = null,

    val positionY: Double? = null
)

/**
 * Response DTO for seat.
 */
data class SeatResponse(
    val id: Long,
    val levelId: Long,
    val levelName: String,
    val seatIdentifier: String,
    val seatNumber: String?,
    val rowLabel: String?,
    val positionX: Double?,
    val positionY: Double?,
    val fullDisplayName: String,
    val createdAt: String
)

/**
 * Batch seat creation request.
 */
data class BatchSeatRequest(
    @field:NotNull(message = "Level ID is required")
    val levelId: Long,

    @field:NotEmpty(message = "Seats list cannot be empty")
    val seats: List<SeatBatchItem>
)

/**
 * Individual seat in batch creation.
 */
data class SeatBatchItem(
    @field:NotBlank(message = "Seat identifier is required")
    val seatIdentifier: String,

    val seatNumber: String? = null,
    val rowLabel: String? = null,
    val positionX: Double? = null,
    val positionY: Double? = null,
)

// ===========================================
// TRANSLATION DTOs
// ===========================================

/**
 * Request DTO for level translation.
 */
data class LevelTranslationRequest(
    @field:NotBlank(message = "Language code is required")
    @field:Pattern(regexp = "^[a-z]{2}$", message = "Language code must be 2 lowercase letters")
    val language: String,

    @field:NotBlank(message = "Translated label is required")
    @field:Size(max = 255, message = "Label must not exceed 255 characters")
    val levelLabel: String
)

/**
 * Request DTO for seat translation.
 */
data class SeatTranslationRequest(
    @field:NotBlank(message = "Language code is required")
    @field:Pattern(regexp = "^[a-z]{2}$", message = "Language code must be 2 lowercase letters")
    val language: String,

    @field:NotBlank(message = "Translated label is required")
    @field:Size(max = 255, message = "Label must not exceed 255 characters")
    val label: String
)

