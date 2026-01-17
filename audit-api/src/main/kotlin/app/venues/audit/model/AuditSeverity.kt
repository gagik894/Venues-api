package app.venues.audit.model

/**
 * Severity levels for audit events.
 * Used for alerting thresholds and compliance reporting.
 */
enum class AuditSeverity {
    /** Routine operations (view, list, normal business flow). */
    INFO,

    /** Significant changes requiring attention (event publish, pricing update). */
    IMPORTANT,

    /** Security-critical events requiring immediate review (login failures, permission changes). */
    CRITICAL
}
