package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.staff.api.dto.AuthorizedVenueDto
import app.venues.staff.domain.OrganizationRole
import app.venues.staff.domain.StaffIdentity
import app.venues.staff.domain.VenueRole
import app.venues.staff.repository.StaffIdentityRepository
import app.venues.venue.api.VenueApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Builds the staff authorized venues list.
 *
 * This service constructs a flat list of venues the staff member can access
 * along with their assigned role for each venue.
 *
 * Used by:
 * - Auth services to build context during login/registration
 * - Management services to refresh context after membership changes
 * - Frontend to build navigation menus and permission checks
 */
@Service
@Transactional(readOnly = true)
class StaffContextBuilder(
    private val staffRepository: StaffIdentityRepository,
    private val venueApi: VenueApi
) {

    /**
     * Builds the complete list of authorized venues for a staff member.
     *
     * Returns:
     * - All venues they have access to across all organizations
     * - Their specific role for each venue (MANAGER, SCANNER, etc.)
     * - Venue basic information (ID, name)
     *
     * @param staff The staff identity entity (must be managed)
     * @return List of authorized venues with roles
     */
    fun buildAuthorizedVenues(staff: StaffIdentity): List<AuthorizedVenueDto> {
        val activeMemberships = staff.memberships.filter { it.isActive }

        // Explicit venue permissions
        val explicitPermissions = activeMemberships
            .flatMap { membership -> membership.venuePermissions.map { it.venueId to it.role } }
            .associate { it }

        // Org admin/owner implied access to all venues in their org
        val adminOrgIds = activeMemberships
            .filter { it.orgRole in listOf(OrganizationRole.OWNER, OrganizationRole.ADMIN) }
            .map { it.organizationId }
            .toSet()

        val impliedAdminVenues = adminOrgIds
            .flatMap { orgId -> venueApi.getVenueIdsByOrganizationId(orgId) }
            .associateWith { VenueRole.MANAGER } // treat org admins as managers for venue-level access

        // Merge explicit and implied, explicit wins
        val mergedRoles = impliedAdminVenues.toMutableMap()
        mergedRoles.putAll(explicitPermissions)

        if (mergedRoles.isEmpty()) {
            return emptyList()
        }

        val venueInfos = venueApi.getVenueBasicInfoBatch(mergedRoles.keys.toSet())

        return mergedRoles
            .mapNotNull { (venueId, role) ->
                venueInfos[venueId]?.let { info ->
                    AuthorizedVenueDto(
                        id = info.id,
                        name = info.name,
                        slug = info.slug,
                        role = role
                    )
                }
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Builds authorized venues list by staff ID.
     *
     * Loads the staff entity from database and builds the authorized venues list.
     *
     * @param staffId Staff member UUID
     * @return List of authorized venues with roles
     * @throws VenuesException.ResourceNotFound if staff not found
     */
    fun buildAuthorizedVenuesById(staffId: UUID): List<AuthorizedVenueDto> {
        val staff = staffRepository.findById(staffId)
            .orElseThrow {
                VenuesException.ResourceNotFound(
                    "Staff not found",
                    "STAFF_NOT_FOUND"
                )
            }
        return buildAuthorizedVenues(staff)
    }
}
