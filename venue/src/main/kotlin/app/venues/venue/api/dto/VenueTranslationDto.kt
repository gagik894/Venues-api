package app.venues.venue.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Request DTO for adding/updating venue translation.
 */
@Serializable
data class VenueTranslationRequest(
    /**
     * Language code (ISO 639-1: en, fr, es, hy, ru, etc.)
     */
    @field:NotBlank(message = "Language code is required")
    @field:Pattern(regexp = "^[a-z]{2}$", message = "Language code must be 2 lowercase letters")
    val language: String,

    /**
     * Translated venue name
     */
    @field:NotBlank(message = "Translated name is required")
    @field:Size(max = 255, message = "Name must not exceed 255 characters")
    val name: String,

    /**
     * Translated venue description
     */
    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null
)

/**
 * Response DTO for venue translation.
 */
@Serializable
data class VenueTranslationResponse(
    val id: Long,
    val language: String,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val lastModifiedAt: Instant
)
