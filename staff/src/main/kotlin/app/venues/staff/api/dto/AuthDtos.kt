package app.venues.staff.api.dto

import app.venues.staff.domain.OrganizationRole
import app.venues.staff.domain.StaffStatus
import app.venues.staff.domain.VenueRole
import java.util.*

/**
 * Basic Profile Info (No permissions here).
 */
data class StaffProfileDto(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val status: StaffStatus,
    val isPlatformSuperAdmin: Boolean
)

/**
 * THE HIERARCHY.
 * This tells the Frontend: "Here are the Orgs this user belongs to,
 * and the specific Venues inside those Orgs they can access."
 */
data class StaffGlobalContextDto(
    val memberships: List<OrganizationMembershipDto>
)

data class OrganizationMembershipDto(
    val organizationId: UUID,
    val orgRole: OrganizationRole, // OWNER, ADMIN, MEMBER
    val venuePermissions: List<VenuePermissionDto>
)

data class VenuePermissionDto(
    val venueId: UUID,
    val venueName: String? = null, // Added for UI
    val venueSlug: String? = null, // Added for UI navigation
    val role: VenueRole // MANAGER, SCANNER, etc.
)

/**
 * Response DTO for successful staff authentication.
 *
 * Contains JWT token, user profile, and organizational context.
 */
data class StaffAuthResponse(
    val token: String,
    val expiresIn: Long,
    val profile: StaffProfileDto,
    val context: StaffGlobalContextDto
)