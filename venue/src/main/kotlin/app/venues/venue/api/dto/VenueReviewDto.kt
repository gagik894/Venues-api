package app.venues.venue.api.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Request DTO for adding/updating venue review.
 */
@Serializable
data class VenueReviewRequest(
    /**
     * Rating from 1 to 5 stars
     */
    @field:NotNull(message = "Rating is required")
    @field:Min(value = 1, message = "Rating must be at least 1")
    @field:Max(value = 5, message = "Rating must not exceed 5")
    val rating: Int,

    /**
     * Optional written comment
     */
    @field:Size(max = 1000, message = "Comment must not exceed 1000 characters")
    val comment: String? = null
)

/**
 * Response DTO for venue review.
 */
@Serializable
data class VenueReviewResponse(
    val id: Long,
    val rating: Int,
    val comment: String?,
    val userId: Long,
    val isModerated: Boolean,
    val createdAt: Instant,
    val lastModifiedAt: Instant
)
