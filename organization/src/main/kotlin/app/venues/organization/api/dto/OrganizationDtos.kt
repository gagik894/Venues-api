package app.venues.organization.api.dto

import app.venues.organization.domain.OrganizationType
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

/**
 * Response DTO for organization information (public).
 */
data class OrganizationResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val description: String?,
    val type: OrganizationType,
    val citySlug: String?,
    val cityName: String?,
    val phoneNumber: String?,
    val email: String?,
    val website: String?,
    val logoUrl: String?,
    val venueCount: Int = 0,
    val createdAt: Instant?,
    val lastModifiedAt: Instant?
)

/**
 * Detailed organization response (admin).
 */
data class OrganizationDetailResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val legalName: String?,
    val taxId: String?,
    val registrationNumber: String?,
    val description: String?,
    val type: OrganizationType,
    val address: String?,
    val citySlug: String?,
    val cityName: String?,
    val phoneNumber: String?,
    val email: String?,
    val website: String?,
    val socialLinks: Map<String, String>?,
    val logoUrl: String?,
    val isActive: Boolean,
    val staffCount: Long = 0,
    val venueCount: Int = 0,
    val createdAt: Instant?,
    val lastModifiedAt: Instant?
)

/**
 * Request DTO to create organization.
 */
data class CreateOrganizationRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 255, message = "Name must be 2-255 characters")
    val name: String,

    @field:NotBlank(message = "Slug is required")
    @field:Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase alphanumeric with hyphens")
    @field:Size(min = 2, max = 100, message = "Slug must be 2-100 characters")
    val slug: String,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,

    val type: OrganizationType = OrganizationType.PRIVATE,

    val legalName: String? = null,
    val taxId: String? = null,
    val registrationNumber: String? = null,

    val cityId: Long? = null,
    val address: String? = null,

    @field:Email(message = "Email must be valid")
    val email: String? = null,

    val phoneNumber: String? = null,
    val website: String? = null,
    val logoUrl: String? = null
)

/**
 * Request DTO to update organization.
 */
data class UpdateOrganizationRequest(
    @field:Size(min = 2, max = 255, message = "Name must be 2-255 characters")
    val name: String? = null,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,

    val type: OrganizationType? = null,

    val legalName: String? = null,
    val taxId: String? = null,
    val registrationNumber: String? = null,

    val cityId: Long? = null,
    val address: String? = null,

    @field:Email(message = "Email must be valid")
    val email: String? = null,

    val phoneNumber: String? = null,
    val website: String? = null,
    val socialLinks: Map<String, String>? = null,
    val logoUrl: String? = null,

    val isActive: Boolean? = null
)
