package app.venues.staff.service

import app.venues.staff.api.StaffSecurityFacade
import app.venues.staff.domain.OrganizationRole
import app.venues.staff.domain.VenueRole
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
     * 3. Venue Manager -> must have MANAGER role for that venue
     */
    override fun canManageVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean {
        return hasVenueAccess(
            staffId = staffId,
            organizationId = organizationId,
            venueId = venueId,
            allowedRoles = setOf(VenueRole.MANAGER)
        )
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

    /**
     * Checks if staff can edit venue content (events, details, etc.).
     *
     * Logic:
     * 1. Super Admin -> allow
     * 2. Org Owner/Admin -> allow
     * 3. Venue MANAGER or EDITOR -> allow
     */
    override fun canEditVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean {
        return hasVenueAccess(
            staffId = staffId,
            organizationId = organizationId,
            venueId = venueId,
            allowedRoles = setOf(VenueRole.MANAGER, VenueRole.EDITOR)
        )
    }

    /**
     * Checks if staff can sell tickets/operate box office.
     *
     * Logic:
     * 1. Super Admin -> allow
     * 2. Org Owner/Admin -> allow
     * 3. Venue MANAGER or SELLER -> allow
     */
    override fun canSellAtVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean {
        return hasVenueAccess(
            staffId = staffId,
            organizationId = organizationId,
            venueId = venueId,
            allowedRoles = setOf(VenueRole.MANAGER, VenueRole.SELLER)
        )
    }

    /**
     * Checks if staff can scan tickets at the venue.
     *
     * Logic:
     * 1. Super Admin -> allow
     * 2. Org Owner/Admin -> allow
     * 3. Venue MANAGER/SELLER/SCANNER -> allow
     */
    override fun canScanAtVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean {
        return hasVenueAccess(
            staffId = staffId,
            organizationId = organizationId,
            venueId = venueId,
            allowedRoles = setOf(VenueRole.MANAGER, VenueRole.SELLER, VenueRole.SCANNER)
        )
    }

    /**
     * Checks if staff can view venue data (read-only).
     *
     * Logic:
     * 1. Super Admin -> allow
     * 2. Org Owner/Admin -> allow
     * 3. Any venue role -> allow
     */
    override fun canViewVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean {
        return hasVenueAccess(
            staffId = staffId,
            organizationId = organizationId,
            venueId = venueId,
            allowedRoles = VenueRole.values().toSet()
        )
    }

    /**
     * Shared permission evaluator for venue-scoped actions.
     *
     * Order of checks:
     * 1. Super Admin -> allow
     * 2. Active membership in org -> required
     * 3. Org Owner/Admin -> allow
     * 4. Venue role must be in allowedRoles
     */
    private fun hasVenueAccess(
        staffId: UUID,
        organizationId: UUID,
        venueId: UUID,
        allowedRoles: Set<VenueRole>
    ): Boolean {
        val staff = staffRepository.findById(staffId).orElse(null) ?: return false

        if (staff.isPlatformSuperAdmin) {
            return true
        }

        val membership = staff.memberships.firstOrNull {
            it.organizationId == organizationId && it.isActive
        } ?: return false

        if (membership.orgRole in listOf(OrganizationRole.OWNER, OrganizationRole.ADMIN)) {
            return true
        }

        return membership.venuePermissions.any { it.venueId == venueId && it.role in allowedRoles }
    }
}
