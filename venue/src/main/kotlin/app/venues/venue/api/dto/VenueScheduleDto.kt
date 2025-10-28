package app.venues.venue.api.dto

import app.venues.venue.domain.DayOfWeek
import jakarta.validation.constraints.Pattern
import kotlinx.serialization.Serializable

/**
 * Request DTO for setting venue operating schedule.
 */
@Serializable
data class VenueScheduleRequest(
    /**
     * Day of the week
     */
    val dayOfWeek: DayOfWeek,

    /**
     * Opening time in HH:MM format (24-hour)
     * Null if closed on this day
     */
    @field:Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Time must be in HH:MM format")
    val openTime: String? = null,

    /**
     * Closing time in HH:MM format (24-hour)
     * Null if closed on this day
     */
    @field:Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Time must be in HH:MM format")
    val closeTime: String? = null,

    /**
     * Whether the venue is closed on this day
     */
    val isClosed: Boolean = false
)

/**
 * Response DTO for venue operating schedule.
 */
@Serializable
data class VenueScheduleResponse(
    val id: Long,
    val dayOfWeek: DayOfWeek,
    val openTime: String?,
    val closeTime: String?,
    val isClosed: Boolean
)
