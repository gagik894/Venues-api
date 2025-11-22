package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.staff.api.dto.OrganizationMembershipDto
import app.venues.staff.api.dto.StaffGlobalContextDto
import app.venues.staff.api.dto.VenuePermissionDto
import app.venues.staff.domain.StaffIdentity
import app.venues.staff.repository.StaffIdentityRepository
import app.venues.venue.api.VenueApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Builds the staff global context (organizations and venues hierarchy).
 *
 * This service constructs the organizational hierarchy that determines
 * which organizations and specific venues a staff member can access.
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
     * Builds the complete organizational context for a staff member.
     *
     * Returns:
     * - All active organizations they belong to
     * - Their role in each organization (OWNER, ADMIN, MEMBER)
     * - Specific venue permissions within each organization
     *
     * @param staff The staff identity entity (must be managed)
     * @return StaffGlobalContextDto with full hierarchy
     */
    fun buildContext(staff: StaffIdentity): StaffGlobalContextDto {
        // Collect all venue IDs to fetch names/slugs in batch
        val allVenueIds = staff.memberships
            .flatMap { it.venuePermissions }
            .map { it.venueId }
            .toSet()

        val venueInfos = if (allVenueIds.isNotEmpty()) {
            venueApi.getVenueBasicInfoBatch(allVenueIds)
        } else {
            emptyMap()
        }

        val memberships = staff.memberships
            .filter { it.isActive }
            .map { membership ->
                OrganizationMembershipDto(
                    organizationId = membership.organizationId,
                    orgRole = membership.orgRole,
                    venuePermissions = membership.venuePermissions
                        .map { vp ->
                            val info = venueInfos[vp.venueId]
                            VenuePermissionDto(
                                venueId = vp.venueId,
                                venueName = info?.name ?: "Unknown Venue",
                                venueSlug = info?.slug,
                                role = vp.role
                            )
                        }
                )
            }

        return StaffGlobalContextDto(memberships = memberships)
    }

    /**
     * Builds context by staff ID.
     *
     * Loads the staff entity from database and builds the context.
     *
     * @param staffId Staff member UUID
     * @return StaffGlobalContextDto with full hierarchy
     * @throws VenuesException.ResourceNotFound if staff not found
     */
    fun buildContextById(staffId: UUID): StaffGlobalContextDto {
        val staff = staffRepository.findById(staffId)
            .orElseThrow {
                VenuesException.ResourceNotFound(
                    "Staff not found",
                    "STAFF_NOT_FOUND"
                )
            }
        return buildContext(staff)
    }
}
