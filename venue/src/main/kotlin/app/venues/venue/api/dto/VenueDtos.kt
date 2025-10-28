package app.venues.venue.api.dto

import app.venues.common.constants.AppConstants
import app.venues.venue.domain.DiscountType
import app.venues.venue.domain.VenueStatus
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant

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

    @field:Size(max = 100, message = "City must not exceed 100 characters")
    val city: String? = null,

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

    @field:Size(max = 100, message = "City must not exceed 100 characters")
    val city: String? = null,

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
 */
data class VenueResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val address: String,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?,
    val email: String,
    val phoneNumber: String?,
    val website: String?,
    val customDomain: String?,
    val category: String?,
    val isAlwaysOpen: Boolean,
    val verified: Boolean,
    val official: Boolean,
    val status: VenueStatus,
    val emailVerified: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val lastModifiedAt: Instant,
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

// ===========================================
// VENUE SCHEDULE
// ===========================================

/**
 * Request DTO for creating/updating venue schedule.
 */
data class VenueScheduleRequest(

    @field:NotNull(message = "Day of week is required")
    val dayOfWeek: DayOfWeek,

    val openTime: String? = null, // Format: "HH:mm"

    val closeTime: String? = null, // Format: "HH:mm"

    @field:NotNull(message = "isClosed flag is required")
    val isClosed: Boolean = false
)

/**
 * Response DTO for venue schedule.
 */
data class VenueScheduleResponse(
    val id: Long,
    val dayOfWeek: DayOfWeek,
    val openTime: String?, // Format: "HH:mm"
    val closeTime: String?, // Format: "HH:mm"
    val isClosed: Boolean
)

// ===========================================
// VENUE TRANSLATION
// ===========================================

/**
 * Request DTO for creating/updating venue translation.
 */
data class VenueTranslationRequest(

    @field:NotBlank(message = "Language code is required")
    @field:Size(min = 2, max = 10, message = "Language code must be between 2 and 10 characters")
    val language: String,

    @field:NotBlank(message = "Translated name is required")
    @field:Size(max = 255, message = "Name must not exceed 255 characters")
    val name: String,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null
)

/**
 * Response DTO for venue translation.
 */
data class VenueTranslationResponse(
    val id: Long,
    val language: String,
    val name: String,
    val description: String?
)

// ===========================================
// VENUE PHOTOS
// ===========================================

/**
 * Request DTO for adding venue photo.
 */
data class VenuePhotoRequest(

    @field:NotBlank(message = "Photo URL is required")
    @field:Size(max = 500, message = "URL must not exceed 500 characters")
    val url: String,

    @field:Size(max = 500, message = "Caption must not exceed 500 characters")
    val caption: String? = null,

    val displayOrder: Int = 0
)

/**
 * Response DTO for venue photo.
 */
data class VenuePhotoResponse(
    val id: Long,
    val url: String,
    val caption: String?,
    val displayOrder: Int,
    val userId: Long,
    val createdAt: Instant
)

// ===========================================
// VENUE REVIEWS
// ===========================================

/**
 * Request DTO for creating/updating venue review.
 */
data class VenueReviewRequest(

    @field:Min(value = 1, message = "Rating must be at least 1")
    @field:Max(value = 5, message = "Rating must not exceed 5")
    val rating: Int,

    @field:Size(max = 2000, message = "Comment must not exceed 2000 characters")
    val comment: String? = null
)

/**
 * Response DTO for venue review.
 */
data class VenueReviewResponse(
    val id: Long,
    val userId: Long,
    val rating: Int,
    val comment: String?,
    val createdAt: Instant,
    val lastModifiedAt: Instant
)

// ===========================================
// VENUE PROMO CODES
// ===========================================

/**
 * Request DTO for creating venue promo code.
 */
data class VenuePromoCodeRequest(

    @field:NotBlank(message = "Promo code is required")
    @field:Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
    val code: String,

    @field:Size(max = 255, message = "Description must not exceed 255 characters")
    val description: String? = null,

    @field:NotNull(message = "Discount type is required")
    val discountType: DiscountType,

    @field:NotNull(message = "Discount value is required")
    @field:DecimalMin(value = "0.0", message = "Discount value must be positive")
    val discountValue: BigDecimal,

    @field:DecimalMin(value = "0.0", message = "Minimum order amount must be positive")
    val minOrderAmount: BigDecimal? = null,

    @field:DecimalMin(value = "0.0", message = "Maximum discount amount must be positive")
    val maxDiscountAmount: BigDecimal? = null,

    @field:Min(value = 1, message = "Max usage count must be at least 1")
    val maxUsageCount: Int? = null,

    val expiresAt: Instant? = null
)

/**
 * Response DTO for venue promo code.
 */
data class VenuePromoCodeResponse(
    val id: Long,
    val code: String,
    val description: String?,
    val discountType: DiscountType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val maxDiscountAmount: BigDecimal?,
    val maxUsageCount: Int?,
    val currentUsageCount: Int,
    val expiresAt: Instant?,
    val isActive: Boolean,
    val createdAt: Instant
)

// ===========================================
// PASSWORD CHANGE
// ===========================================

/**
 * Request DTO for venue password change.
 */
data class VenuePasswordChangeRequest(

    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(
        min = AppConstants.Validation.MIN_PASSWORD_LENGTH,
        max = AppConstants.Validation.MAX_PASSWORD_LENGTH,
        message = "Password must be between ${AppConstants.Validation.MIN_PASSWORD_LENGTH} and ${AppConstants.Validation.MAX_PASSWORD_LENGTH} characters"
    )
    val newPassword: String
)

