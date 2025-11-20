package app.venues.staff.api

import java.util.*

/**
 * API Facade for Staff Security.
 * Other modules (Venue, Organization) use this to check permissions without touching the DB.
 */
interface StaffSecurityFacade {
    /**
     * Checks if a specific StaffIdentity has permission to manage a specific Venue.
     * logic: SuperAdmin -> OrgAdmin -> VenueManager
     */
    fun canManageVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean

    /**
     * Checks if a staff member has high-level administrative rights for an Organization.
     */
    fun isOrganizationAdmin(staffId: UUID, organizationId: UUID): Boolean
}