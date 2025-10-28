package app.venues.venue.api.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Request DTO for adding venue photo.
 */
@Serializable
data class VenuePhotoRequest(
    /**
     * URL/path to the photo file
     */
    @field:NotBlank(message = "Photo URL is required")
    @field:Size(max = 500, message = "URL must not exceed 500 characters")
    val url: String,

    /**
     * Optional caption for the photo
     */
    @field:Size(max = 500, message = "Caption must not exceed 500 characters")
    val caption: String? = null,

    /**
     * Display order for sorting photos (0 = first)
     */
    @field:Min(value = 0, message = "Display order must be non-negative")
    @field:Max(value = 999, message = "Display order must not exceed 999")
    val displayOrder: Int = 0
)

/**
 * Response DTO for venue photo.
 */
@Serializable
data class VenuePhotoResponse(
    val id: Long,
    val url: String,
    val caption: String?,
    val displayOrder: Int,
    val userId: Long,
    val createdAt: Instant
)
