package app.venues.venue.api.dto

import app.venues.common.constants.AppConstants
import app.venues.venue.domain.VenueOwnership
import app.venues.venue.domain.VenueStatus
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.*

// ===========================================
// PUBLIC VENUE RESPONSES
// ===========================================

/**
 * Minimal venue identifier for ISR static path generation.
 * Used by Next.js to build domain → venueId mappings.
 *
 * @property id Immutable venue UUID (use as ISR cache key)
 * @property slug Human-readable identifier (for debugging/logs)
 * @property customDomain Optional vanity domain (for middleware routing)
 */
data class VenueIdentifierDto(
    val id: UUID,
    val slug: String,
    val customDomain: String?
)

/**
 * Public venue information (list view).
 * Used for venue discovery, search results, and listings.
 */
data class VenueResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val coverImageUrl: String?,

    val citySlug: String,
    val cityName: String,

    val categoryCode: String?,
    val categoryName: String?,
    val categoryColor: String?,

    val phoneNumber: String?,
    val website: String?,
    val status: VenueStatus,

    val followerCount: Long? = null,
    val averageRating: Double? = null
)

/**
 * Detailed venue information (single venue view).
 * Includes full details, schedules, and social links.
 */
data class VenueDetailResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val coverImageUrl: String?,

    val address: String,
    val citySlug: String,
    val cityName: String,
    val latitude: Double?,
    val longitude: Double?,
    val timeZone: String,

    val categoryCode: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val categoryIcon: String?,

    val phoneNumber: String?,
    val website: String?,
    val contactEmail: String?,
    val socialLinks: Map<String, String>?,

    val isAlwaysOpen: Boolean,
    val customDomain: String?,
    val status: VenueStatus,

    val translations: List<VenueTranslationDto>,
    val schedules: List<VenueScheduleDto>,

    val followerCount: Long,
    val averageRating: Double?,
    val reviewCount: Long,

    val createdAt: Instant?,
    val lastModifiedAt: Instant?
)

// ===========================================
// ADMIN/OWNER RESPONSES
// ===========================================

/**
 * Admin venue information (includes sensitive data).
 * Used for venue management dashboard.
 */
data class VenueAdminResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val legalName: String?,
    val taxId: String?,
    val description: String?,

    val address: String,
    val citySlug: String,
    val cityName: String,
    val latitude: Double?,
    val longitude: Double?,
    val timeZone: String,

    val categoryCode: String?,
    val categoryName: String?,

    val phoneNumber: String?,
    val website: String?,
    val contactEmail: String?,
    val socialLinks: Map<String, String>?,

    val ownershipType: VenueOwnership?,
    val notificationEmails: List<String>,

    val logoUrl: String?,
    val coverImageUrl: String?,
    val customDomain: String?,
    val isAlwaysOpen: Boolean,

    val status: VenueStatus,

    val createdAt: Instant?,
    val lastModifiedAt: Instant?
)

// ===========================================
// CREATE/UPDATE REQUESTS
// ===========================================

/**
 * Request to create a new venue (admin/staff only).
 */
data class CreateVenueRequest(
    @field:NotNull(message = "Organization ID is required")
    val organizationId: UUID,

    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 255, message = "Name must be 2-255 characters")
    val name: String,

    @field:NotBlank(message = "Slug is required")
    @field:Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase alphanumeric with hyphens")
    @field:Size(min = 2, max = 100, message = "Slug must be 2-100 characters")
    val slug: String,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,

    @field:NotBlank(message = "Address is required")
    @field:Size(max = 500, message = "Address must not exceed 500 characters")
    val address: String,

    //TODO: USE CITY CODE INSTEAD OF ID
    @field:NotNull(message = "City is required")
    var cityId: Long,

    val categoryCode: String? = null,

    val legalName: String? = null,
    val taxId: String? = null,

    @field:DecimalMin(value = "-90.0")
    @field:DecimalMax(value = "90.0")
    val latitude: Double? = null,

    @field:DecimalMin(value = "-180.0")
    @field:DecimalMax(value = "180.0")
    val longitude: Double? = null,

    @field:Pattern(regexp = AppConstants.Patterns.PHONE, message = "Invalid phone number")
    val phoneNumber: String? = null,

    val website: String? = null,
    val contactEmail: String? = null,

    val ownershipType: VenueOwnership? = null,
    val timeZone: String = "Asia/Yerevan"
)

/**
 * Request to update venue information (owner/admin only).
 */
data class UpdateVenueRequest(
    @field:Size(min = 2, max = 255, message = "Name must be 2-255 characters")
    val name: String? = null,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,

    @field:Size(max = 500, message = "Address must not exceed 500 characters")
    val address: String? = null,

    val cityId: Long? = null,
    val categoryCode: String? = null,

    val legalName: String? = null,
    val taxId: String? = null,

    @field:DecimalMin(value = "-90.0")
    @field:DecimalMax(value = "90.0")
    val latitude: Double? = null,

    @field:DecimalMin(value = "-180.0")
    @field:DecimalMax(value = "180.0")
    val longitude: Double? = null,

    @field:Pattern(regexp = AppConstants.Patterns.PHONE, message = "Invalid phone number")
    val phoneNumber: String? = null,

    val website: String? = null,
    val contactEmail: String? = null,

    val socialLinks: Map<String, String>? = null,
    val notificationEmails: List<String>? = null,

    val logoUrl: String? = null,
    val coverImageUrl: String? = null,
    val customDomain: String? = null,
    val isAlwaysOpen: Boolean? = null,

    val ownershipType: VenueOwnership? = null,
    val timeZone: String? = null
)

// ===========================================
// SUPPORTING DTOs
// ===========================================

/**
 * Venue translation information.
 */
data class VenueTranslationDto(
    val language: String,
    val name: String,
    val description: String?
)

/**
 * Venue operating schedule.
 */
data class VenueScheduleDto(
    val dayOfWeek: String,
    val openTime: String?,
    val closeTime: String?,
    val isClosed: Boolean
)

/**
 * Venue category information (public).
 */
data class VenueCategoryDto(
    val code: String,
    val name: String,
    val color: String?,
    val icon: String?,
    val displayOrder: Int
)
