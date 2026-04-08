package app.venues.audit.service

import app.venues.audit.model.AuditAction
import app.venues.audit.model.StaffAuditEntry
import java.util.*

/**
 * Interface for programmatic audit logging.
 * Use this for manual audit calls outside of @Auditable-annotated controllers.
 *
 * Prefer using @Auditable annotation on controller methods where possible.
 */
interface AuditActionRecorder {

    /**
     * Record a successful action.
     */
    fun success(
        action: String,
        staffId: UUID?,
        venueId: UUID?,
        subjectType: String,
        subjectId: String?,
        organizationId: UUID? = null,
        metadata: Map<String, Any?> = emptyMap()
    )

    /**
     * Record a failed action.
     */
    fun failure(
        action: String,
        staffId: UUID?,
        venueId: UUID?,
        subjectType: String,
        subjectId: String?,
        organizationId: UUID? = null,
        metadata: Map<String, Any?> = emptyMap(),
        reason: String? = null
    )

    /**
     * Create a builder for more complex audit entries.
     */
    fun builder(staffId: UUID, action: AuditAction): StaffAuditEntry.Builder

    /**
     * Create a builder from action string.
     */
    fun builder(staffId: UUID, action: String): StaffAuditEntry.Builder
}
