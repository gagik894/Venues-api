package app.venues.audit.model

import app.venues.audit.model.StaffAuditEntry.Companion.builder
import java.time.Instant
import java.util.*

/**
 * Immutable entry for the staff audit log.
 * Use [builder] to construct instances with a fluent API.
 */
data class StaffAuditEntry(
    /** Staff member who performed the action. Required. */
    val staffId: UUID,

    /** Action performed. */
    val action: AuditAction,

    /** When the action occurred. Defaults to now. */
    val occurredAt: Instant = Instant.now(),

    /** Optional platform ID if action was via platform API. */
    val platformId: UUID? = null,

    /** Venue context (if applicable). */
    val venueId: UUID? = null,

    /** Organization context (if applicable). */
    val organizationId: UUID? = null,

    /** Category for filtering. Defaults to action's category. */
    val category: AuditCategory = action.category,

    /** Severity for alerting. Defaults to action's severity. */
    val severity: AuditSeverity = action.severity,

    /** Type of entity affected (e.g., "event", "venue", "ticket"). */
    val subjectType: String? = null,

    /** ID of the affected entity. */
    val subjectId: String? = null,

    /** Human-readable description of what happened. */
    val description: String? = null,

    /** For UPDATE actions: map of field changes with old/new values. */
    val changes: Map<String, FieldChange>? = null,

    /** Outcome of the action. */
    val outcome: AuditOutcome = AuditOutcome.SUCCESS,

    /** If outcome is FAILURE, the reason. */
    val failureReason: String? = null,

    /** Client IP address for forensics. */
    val clientIp: String? = null,

    /** User agent for forensics. */
    val userAgent: String? = null,

    /** Additional action-specific metadata. */
    val metadata: Map<String, Any?> = emptyMap()
) {
    companion object {
        /**
         * Start building an audit entry.
         */
        fun builder(staffId: UUID, action: AuditAction) = Builder(staffId, action)

        /**
         * Start building an audit entry from an action string (legacy support).
         */
        fun builder(staffId: UUID, actionString: String) =
            Builder(staffId, AuditAction.fromString(actionString))
    }

    /**
     * Fluent builder for constructing audit entries.
     */
    class Builder(
        private val staffId: UUID,
        private val action: AuditAction
    ) {
        private var occurredAt: Instant = Instant.now()
        private var platformId: UUID? = null
        private var venueId: UUID? = null
        private var organizationId: UUID? = null
        private var category: AuditCategory = action.category
        private var severity: AuditSeverity = action.severity
        private var subjectType: String? = null
        private var subjectId: String? = null
        private var description: String? = null
        private var changes: Map<String, FieldChange>? = null
        private var outcome: AuditOutcome = AuditOutcome.SUCCESS
        private var failureReason: String? = null
        private var clientIp: String? = null
        private var userAgent: String? = null
        private var metadata: MutableMap<String, Any?> = mutableMapOf()

        fun occurredAt(instant: Instant) = apply { occurredAt = instant }
        fun platformId(id: UUID?) = apply { platformId = id }
        fun venueId(id: UUID?) = apply { venueId = id }
        fun organizationId(id: UUID?) = apply { organizationId = id }
        fun category(cat: AuditCategory) = apply { category = cat }
        fun severity(sev: AuditSeverity) = apply { severity = sev }

        fun subject(type: String, id: String?) = apply {
            subjectType = type
            subjectId = id
        }

        fun subject(type: String, id: UUID?) = apply {
            subjectType = type
            subjectId = id?.toString()
        }

        fun description(desc: String?) = apply { description = desc }

        fun changes(fieldChanges: Map<String, FieldChange>?) = apply { changes = fieldChanges }

        fun change(field: String, oldValue: Any?, newValue: Any?) = apply {
            val current = changes?.toMutableMap() ?: mutableMapOf()
            current[field] = FieldChange(oldValue, newValue)
            changes = current
        }

        fun success() = apply { outcome = AuditOutcome.SUCCESS }

        fun failure(reason: String?) = apply {
            outcome = AuditOutcome.FAILURE
            failureReason = reason
        }

        fun clientIp(ip: String?) = apply { clientIp = ip }
        fun userAgent(ua: String?) = apply { userAgent = ua }

        fun metadata(key: String, value: Any?) = apply { metadata[key] = value }
        fun metadata(map: Map<String, Any?>) = apply { metadata.putAll(map) }

        fun build(): StaffAuditEntry = StaffAuditEntry(
            staffId = staffId,
            action = action,
            occurredAt = occurredAt,
            platformId = platformId,
            venueId = venueId,
            organizationId = organizationId,
            category = category,
            severity = severity,
            subjectType = subjectType,
            subjectId = subjectId,
            description = description,
            changes = changes,
            outcome = outcome,
            failureReason = failureReason,
            clientIp = clientIp,
            userAgent = userAgent,
            metadata = metadata.toMap()
        )
    }
}

/**
 * Represents a change to a single field.
 */
data class FieldChange(
    val old: Any?,
    val new: Any?
)
