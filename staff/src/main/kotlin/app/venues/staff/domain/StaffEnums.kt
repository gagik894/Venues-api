package app.venues.staff.domain

/**
 * Organization-wide role.
 */
enum class OrganizationRole {
    OWNER,      // Full access + finance
    ADMIN,      // Full venue access
    MEMBER      // Needs venue permissions
}

/**
 * Venue-level permissions.
 */
enum class VenueRole {
    MANAGER,    // Full venue management
    EDITOR,     // Create/update events
    SCANNER,    // Ticket scanning only
    VIEWER      // Read-only
}

enum class StaffStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    LOCKED,
    DELETED
}