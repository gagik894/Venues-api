package app.venues.venue.api.dto

import app.venues.common.constants.AppConstants
import app.venues.venue.domain.VenueStatus
import jakarta.validation.constraints.*
import java.util.*


// ===========================================
// VENUE REGISTRATION & AUTHENTICATION
// ===========================================

/**
 * Request DTO for venue registration.
 *
 * Validates all required fields before venue account creation.
 */
data class VenueRegistrationRequest(

    @field:NotBlank(message = "Venue name is required")
    @field:Size(
        min = AppConstants.Validation.MIN_NAME_LENGTH,
        max = 255,
        message = "Venue name must be between ${AppConstants.Validation.MIN_NAME_LENGTH} and 255 characters"
    )
    val name: String,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,

    @field:NotBlank(message = "Address is required")
    @field:Size(max = 500, message = "Address must not exceed 500 characters")
    val address: String,

    @field:NotNull(message = "City is required")
    val cityId: Long,

    @field:DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @field:DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    val latitude: Double? = null,

    @field:DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @field:DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    val longitude: Double? = null,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(
        min = AppConstants.Validation.MIN_PASSWORD_LENGTH,
        max = AppConstants.Validation.MAX_PASSWORD_LENGTH,
        message = "Password must be between ${AppConstants.Validation.MIN_PASSWORD_LENGTH} and ${AppConstants.Validation.MAX_PASSWORD_LENGTH} characters"
    )
    val password: String,

    @field:Pattern(
        regexp = AppConstants.Patterns.PHONE,
        message = "Phone number format is invalid"
    )
    val phoneNumber: String? = null,

    @field:Size(max = 500, message = "Website must not exceed 500 characters")
    val website: String? = null,

    @field:Size(max = 50, message = "Category must not exceed 50 characters")
    val category: String? = null
)

/**
 * Request DTO for venue login.
 */
data class VenueLoginRequest(

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

/**
 * Response DTO for successful venue authentication.
 */
data class VenueLoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val venue: VenueResponse
)

// ===========================================
// VENUE PROFILE
// ===========================================

/**
 * Request DTO for updating venue profile.
 */
data class VenueUpdateRequest(

    @field:Size(
        min = AppConstants.Validation.MIN_NAME_LENGTH,
        max = 255,
        message = "Venue name must be between ${AppConstants.Validation.MIN_NAME_LENGTH} and 255 characters"
    )
    val name: String? = null,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,

    @field:Size(max = 500, message = "Address must not exceed 500 characters")
    val address: String? = null,

    val cityId: Long? = null,

    @field:DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @field:DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    val latitude: Double? = null,

    @field:DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @field:DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    val longitude: Double? = null,

    @field:Pattern(
        regexp = AppConstants.Patterns.PHONE,
        message = "Phone number format is invalid"
    )
    val phoneNumber: String? = null,

    @field:Size(max = 500, message = "Website must not exceed 500 characters")
    val website: String? = null,

    @field:Size(max = 50, message = "Category must not exceed 50 characters")
    val category: String? = null,

    val isAlwaysOpen: Boolean? = null
)

/**
 * Response DTO for venue information.
 *
 * City fields are inlined for frontend simplicity:
 * - citySlug: For filtering (?city=yerevan)
 * - cityName: Localized display name
 */
data class VenueResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val address: String,

    // Inlined city fields (frontend-friendly)
    val citySlug: String,
    val cityName: String,

    val latitude: Double?,
    val longitude: Double?,
    val email: String?,
    val phoneNumber: String?,
    val website: String?,
    val customDomain: String?,
    val category: String?,
    val isAlwaysOpen: Boolean,
    val verified: Boolean,
    val official: Boolean,
    val status: VenueStatus,
    val followerCount: Long? = null,
    val averageRating: Double? = null,
    val reviewCount: Long? = null
)

/**
 * Detailed venue response with schedules and translations
 */
data class VenueDetailedResponse(
    val venue: VenueResponse,
    val schedules: List<VenueScheduleResponse>,
    val translations: List<VenueTranslationResponse>,
    val photos: List<VenuePhotoResponse>
)
