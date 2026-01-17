package app.venues.audit.model

/**
 * Categories for grouping audit events by domain.
 * Used for filtering, reporting, and access control.
 */
enum class AuditCategory {
    /** Authentication, authorization, password changes, permission updates. */
    SECURITY,

    /** Ticket sales, refunds, cart operations, bookings. */
    SALES,

    /** Event creation, updates, publishing, session management. */
    EVENT_MANAGEMENT,

    /** Venue settings, seating charts, pricing templates, promo codes. */
    CONFIGURATION,

    /** Platform API operations (external integrations). */
    PLATFORM,

    /** File uploads, deletions, media management. */
    MEDIA,

    /** Fallback for uncategorized actions. */
    SYSTEM
}
