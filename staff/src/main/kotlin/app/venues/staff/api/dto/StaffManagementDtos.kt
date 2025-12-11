package app.venues.staff.api.dto

import app.venues.staff.domain.OrganizationRole
import app.venues.staff.domain.StaffStatus
import app.venues.staff.domain.VenueRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.*

/**
 * Invite a user to an Organization.
 */
data class InviteStaffRequest(
    @field:Email @field:NotBlank val email: String,
    val organizationId: UUID,
    val role: OrganizationRole,
    val venuePermissions: List<VenuePermissionInput> = emptyList(),
    val sendEmail: Boolean = true,
    val preferredLanguage: String? = null
)

/**
 * Venue permission input used during invite/create flows.
 */
data class VenuePermissionInput(
    val venueId: UUID,
    val role: VenueRole
)

/**
 * Detailed staff view.
 */
data class StaffDetailDto(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val status: StaffStatus,
    val isSuperAdmin: Boolean,
    val lastLoginAt: Instant,
    val createdAt: Instant,
    val organizations: List<OrganizationAccessDto>,
    val venueRoles: List<AuthorizedVenueDto>
)

/**
 * Direct staff creation (admin-driven).
 */
data class CreateStaffRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val organizationId: UUID,
    val role: OrganizationRole,
    val venuePermissions: List<VenuePermissionInput> = emptyList(),
    val isSuperAdmin: Boolean = false,
    val sendEmail: Boolean = false,
    val preferredLanguage: String? = null
)

/**
 * Resend an invite to a staff member (pending accounts only).
 */
data class ResendInviteRequest(
    val staffId: UUID,
    val organizationId: UUID,
    val sendEmail: Boolean = true
)

/**
 * Revoke an outstanding invite token.
 */
data class RevokeInviteRequest(
    val staffId: UUID,
    val organizationId: UUID
)

/**
 * Update an organization membership role/active flag.
 */
data class UpdateMembershipRequest(
    val role: OrganizationRole,
    val isActive: Boolean = true
)

/**
 * Update a venue role for a staff member.
 */
data class UpdateVenueRoleRequest(
    val role: VenueRole
)

/**
 * Grant specific venue access to an existing member.
 */
data class GrantVenuePermissionRequest(
    val staffId: UUID, // The target user
    val venueId: UUID,
    val role: VenueRole
)

/**
 * Lightweight staff summary for listings.
 */
data class StaffListItemDto(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val status: StaffStatus,
    val isSuperAdmin: Boolean,
    val organizations: List<OrganizationAccessDto>,
    val venueRoles: List<AuthorizedVenueDto>
)

/**
 * Venue permission listing entry.
 */
data class VenuePermissionDto(
    val staffId: UUID,
    val staffEmail: String,
    val role: VenueRole
)

/**
 * Toggle super admin flag.
 */
data class SetSuperAdminRequest(
    val staffId: UUID,
    val isSuperAdmin: Boolean
)