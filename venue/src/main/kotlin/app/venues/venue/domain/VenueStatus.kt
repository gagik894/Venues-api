package app.venues.venue.domain

/**
 * Venue Status Enum
 *
 * Represents the lifecycle state of a venue in the system.
 *
 * Status Flow:
 * PENDING_APPROVAL -> ACTIVE (after admin approval)
 * ACTIVE -> SUSPENDED (if venue violates policies)
 * ACTIVE -> INACTIVE (if venue owner deactivates)
 * SUSPENDED/INACTIVE -> ACTIVE (can be reactivated)
 */
enum class VenueStatus {
    /**
     * Venue registration is pending admin approval
     * - Cannot post events
     * - Not visible to public
     */
    PENDING_APPROVAL,

    /**
     * Venue is active and operational
     * - Can post events
     * - Visible to public
     * - Full access to all features
     */
    ACTIVE,

    /**
     * Venue is temporarily suspended by admin
     * - Cannot post new events
     * - Existing events may continue
     * - Not visible in search results
     */
    SUSPENDED,

    /**
     * Venue is inactive (deactivated by owner)
     * - Cannot post events
     * - Not visible to public
     * - Can be reactivated by owner
     */
    INACTIVE,

    /**
     * Venue account is permanently deleted
     * - Soft delete for data integrity
     * - Cannot be reactivated
     */
    DELETED
}

