package app.venues.staff.service

import app.venues.staff.api.dto.OrganizationMembershipDto
import app.venues.staff.api.dto.StaffGlobalContextDto
import app.venues.staff.api.dto.VenuePermissionDto
import app.venues.staff.domain.StaffIdentity
import app.venues.staff.repository.StaffIdentityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Builds the staff global context (organizations and venues hierarchy).
 *
 * Used by auth and management services to construct the frontend navigation data.
 */
@Service
@Transactional(readOnly = true)
class StaffContextBuilder(
    private val staffRepository: StaffIdentityRepository
) {

    /**
     * Builds the complete organizational context for a staff member.
     *
     * Returns:
     * - All organizations they belong to
     * - Their role in each organization
     * - Specific venue permissions within each organization
     */
    fun buildContext(staff: StaffIdentity): StaffGlobalContextDto {
        // Reload with memberships
        val staffWithMemberships = staffRepository.findById(staff.id)
            .orElseThrow { IllegalStateException("Staff not found: ${staff.id}") }

        val memberships = staffWithMemberships.memberships
            .filter { it.isActive }
            .map { membership ->
                OrganizationMembershipDto(
                    organizationId = membership.organizationId,
                    orgRole = membership.orgRole,
                    venuePermissions = membership.venuePermissions
                        .map { vp ->
                            VenuePermissionDto(
                                venueId = vp.venueId,
                                role = vp.role
                            )
                        }
                )
            }

        return StaffGlobalContextDto(memberships = memberships)
    }

    /**
     * Builds context by staff ID.
     */
    fun buildContextById(staffId: java.util.UUID): StaffGlobalContextDto {
        val staff = staffRepository.findById(staffId)
            .orElseThrow {
                app.venues.common.exception.VenuesException.ResourceNotFound(
                    "Staff not found",
                    "STAFF_NOT_FOUND"
                )
            }
        return buildContext(staff)
    }
}
