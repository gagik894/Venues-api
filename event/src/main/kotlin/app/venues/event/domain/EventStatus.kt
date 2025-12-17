package app.venues.event.domain

/**
 * Enumeration of possible event statuses.
 *
 * Status Lifecycle:
 * DRAFT → PUBLISHED → ARCHIVED
 *         ↓
 *      SUSPENDED/DELETED
 */
enum class EventStatus {
    /**
     * The Event is being created.
     * VISIBILITY: Hidden (Host only).
     * ACTION: Host can edit details, add photos, set prices.
     */
    DRAFT,

    /**
     * The Event is live.
     * VISIBILITY: Public.
     * ACTION: Users can view the page. Whether they can *buy* depends on Session Status.
     */
    PUBLISHED,

    /**
     * Temporarily taken down (e.g. for maintenance or policy violation).
     * VISIBILITY: Hidden (404 Not Found for users).
     */
    SUSPENDED,

    /**
     * The event is finished and old.
     * VISIBILITY: Visible in "Past Events" lists, but removed from "Upcoming" search index.
     * ACTION: Read-only mode.
     */
    ARCHIVED,

    /**
     * Soft Delete.
     * VISIBILITY: Hidden everywhere except database admin tools.
     */
    DELETED
}

