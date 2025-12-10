package app.venues.staff.api.dto

import app.venues.staff.domain.OrganizationRole
import app.venues.staff.domain.StaffStatus
import app.venues.staff.domain.VenueRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.*

/**
 * Invite a user to an Organization.
 */
data class InviteStaffRequest(
    @field:Email @field:NotBlank val email: String,
    val organizationId: UUID,
    val role: OrganizationRole
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