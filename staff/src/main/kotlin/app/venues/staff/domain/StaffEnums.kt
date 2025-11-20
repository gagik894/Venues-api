package app.venues.staff.domain

/**
 * DEFINES SCOPE: Where does this user have power?
 */
enum class StaffRoleLevel {
    /** * Platform Super Admin.
     * Access: GLOBAL.
     * Organization ID: NULL.
     * Can manage Organizations, Billing, and System Settings.
     */
    SYSTEM,

    /** * Ministry/Company Admin.
     * Access: All Venues belonging to their Organization.
     * Organization ID: MANDATORY.
     */
    ORGANIZATION,

    /** * Venue-specific staff.
     * Access: Defined by entries in the 'staff_venue_scopes' table.
     * Organization ID: Usually NULL (unless they also have a loose affiliation).
     */
    VENUE
}

/**
 * DEFINES PERMISSIONS: What can they do inside a specific venue?
 */
enum class StaffVenueRole {
    OWNER,      // Can edit venue settings, finance, and manage other staff
    MANAGER,    // Can create/edit events, scan tickets, view reports
    EDITOR,     // Can create/edit events content only
    VIEWER      // Read-only access (e.g., financial auditors)
}

enum class StaffStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED, // By Admin
    LOCKED,    // Too many login attempts
    DELETED
}