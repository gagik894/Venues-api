package app.venues.staff.api

import java.util.*

/**
 * API Facade for Staff Security.
 * Other modules (Venue, Organization) use this to check permissions without touching the DB.
 */
interface StaffSecurityFacade {
    /**
     * Checks if a specific StaffIdentity has permission to manage a specific Venue.
     *
     * logic: SuperAdmin -> OrgAdmin -> Venue MANAGER
     */
    fun canManageVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean

    /**
     * Checks if a staff member has high-level administrative rights for an Organization.
     */
    fun isOrganizationAdmin(staffId: UUID, organizationId: UUID): Boolean

    /**
     * Checks if staff can edit venue content (events, details, etc.).
     *
     * logic: SuperAdmin -> OrgAdmin -> Venue MANAGER/EDITOR
     */
    fun canEditVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean

    /**
     * Checks if staff can sell tickets/operate box office for the venue.
     *
     * logic: SuperAdmin -> OrgAdmin -> Venue MANAGER/SELLER
     */
    fun canSellAtVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean

    /**
     * Checks if staff can scan tickets at the venue.
     *
     * logic: SuperAdmin -> OrgAdmin -> Venue MANAGER/SELLER/SCANNER
     */
    fun canScanAtVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean

    /**
     * Checks if staff can view the venue (read-only access).
     *
     * logic: SuperAdmin -> OrgAdmin -> any venue role
     */
    fun canViewVenue(staffId: UUID, venueId: UUID, organizationId: UUID): Boolean
}