package app.venues.staff.service

import app.venues.staff.api.StaffSecurityFacade
import app.venues.staff.domain.OrganizationRole
import app.venues.staff.repository.StaffIdentityRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Implements the StaffSecurityFacade API for cross-module permission checking.
 *
 * Other modules use this facade to check permissions without direct database access.
 */
@Service
@Transactional(readOnly = true)
class StaffSecurityFacadeImpl(
    private val staffRepository: StaffIdentityRepository
) : StaffSecurityFacade {

    private val logger = KotlinLogging.logger {}

    /**
     * Checks if a staff member can manage a specific venue.
     *
     * Logic:
     * 1. Super Admin -> can manage any venue
     * 2. Organization Admin/Owner -> can manage any venue in their org
     * 3. Venue Manager -> can only manage venues they have explicit permission for
     */
    override fun canManageVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean {
        val staff = staffRepository.findById(staffId).orElse(null) ?: return false

        // Super Admin has global access
        if (staff.isPlatformSuperAdmin) {
            return true
        }

        // Find membership for this organization
        val membership = staff.memberships.firstOrNull {
            it.organizationId == organizationId && it.isActive
        } ?: return false

        // Organization Owner/Admin can manage all venues in their org
        if (membership.orgRole in listOf(OrganizationRole.OWNER, OrganizationRole.ADMIN)) {
            return true
        }

        // Regular members need explicit venue permission
        return membership.venuePermissions.any { it.venueId == venueId }
    }

    /**
     * Checks if a staff member is an admin of an organization.
     */
    override fun isOrganizationAdmin(staffId: UUID, organizationId: UUID): Boolean {
        val staff = staffRepository.findById(staffId).orElse(null) ?: return false

        // Super Admin is admin of everything
        if (staff.isPlatformSuperAdmin) {
            return true
        }

        // Check if they have OWNER or ADMIN role for this organization
        return staff.memberships.any {
            it.organizationId == organizationId &&
                    it.isActive &&
                    it.orgRole in listOf(OrganizationRole.OWNER, OrganizationRole.ADMIN)
        }
    }
}
