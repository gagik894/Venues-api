package app.venues.audit.service

import app.venues.audit.model.AuditAction
import app.venues.audit.model.StaffAuditEntry
import app.venues.audit.port.api.StaffAuditPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.*

/**
 * Convenience wrapper for programmatic audit logging.
 * Use this for manual audit calls outside of @Auditable-annotated controllers.
 *
 * Prefer using @Auditable annotation on controller methods where possible.
 */
@Component
class AuditActionRecorder(
    private val staffAuditPort: StaffAuditPort
) {
    private val logger = KotlinLogging.logger {}

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
    ) {
        if (staffId == null) {
            logger.warn { "Cannot record audit without staffId: action=$action" }
            return
        }

        val entry = StaffAuditEntry.builder(staffId, action)
            .venueId(venueId)
            .organizationId(organizationId)
            .subject(subjectType, subjectId)
            .success()
            .metadata(metadata)
            .build()

        staffAuditPort.log(entry)
    }

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
    ) {
        if (staffId == null) {
            logger.warn { "Cannot record audit without staffId: action=$action" }
            return
        }

        val entry = StaffAuditEntry.builder(staffId, action)
            .venueId(venueId)
            .organizationId(organizationId)
            .subject(subjectType, subjectId)
            .failure(reason)
            .metadata(metadata)
            .build()

        staffAuditPort.log(entry)
    }

    /**
     * Create a builder for more complex audit entries.
     */
    fun builder(staffId: UUID, action: AuditAction) = StaffAuditEntry.builder(staffId, action)

    /**
     * Create a builder from action string.
     */
    fun builder(staffId: UUID, action: String) = StaffAuditEntry.builder(staffId, action)
}
