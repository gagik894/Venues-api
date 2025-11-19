package app.venues.venue.domain

/**
 * Defines the permission level for a Staff account.
 */
enum class StaffRole {
    /**
     * Full administrative access. Can manage billing, venue settings, and other staff.
     */
    OWNER,

    /**
     * Standard access. Can manage events, see reports, and day-to-day operations.
     */
    STAFF,

    /**
     * Temporary, restricted access. Can only manage a single, specific event.
     * Used in conjunction with `restrictedToEventId`.
     */
    EVENT_MANAGER
}
