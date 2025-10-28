package app.venues.event.domain

/**
 * Enumeration of possible event statuses.
 *
 * Status Lifecycle:
 * DRAFT → UPCOMING → PAST
 *         ↓
 *      PAUSED/CANCELLED/MAINTENANCE
 *         ↓
 *      ARCHIVED
 */
enum class EventStatus {
    /**
     * Event is published and scheduled for the future
     */
    UPCOMING,

    /**
     * Event is temporarily paused (not visible to public)
     */
    PAUSED,

    /**
     * Event has already occurred
     */
    PAST,

    /**
     * Event has been cancelled
     */
    CANCELLED,

    /**
     * Event is in draft mode (not published)
     */
    DRAFT,

    /**
     * Event is under maintenance (technical issues)
     */
    MAINTENANCE,

    /**
     * Event has been archived (historical record)
     */
    ARCHIVED
}

