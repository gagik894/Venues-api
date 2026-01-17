package app.venues.audit.model

/**
 * Outcome of an audited action.
 */
enum class AuditOutcome {
    /** Action completed successfully. */
    SUCCESS,

    /** Action failed (see failure_reason for details). */
    FAILURE
}
