package app.venues.audit.port.api

import java.time.Instant
import java.util.*

/**
 * @deprecated Use [app.venues.audit.model.AuditOutcome] or related enums.
 */
@Deprecated("Use AuditCategory, AuditSeverity from audit model package")
enum class AuditActorType {
    STAFF,
    USER,
    PLATFORM,
    SYSTEM
}

/**
 * @deprecated Use [app.venues.audit.model.AuditOutcome] instead.
 */
@Deprecated(
    message = "Use app.venues.audit.model.AuditOutcome instead",
    replaceWith = ReplaceWith("app.venues.audit.model.AuditOutcome")
)
enum class AuditOutcome {
    SUCCESS,
    FAILURE
}

/**
 * @deprecated Use [app.venues.audit.model.StaffAuditEntry] instead.
 * This DTO was for the deprecated audit_events table which logged HTTP noise.
 */
@Deprecated(
    message = "Use StaffAuditEntry instead",
    replaceWith = ReplaceWith("StaffAuditEntry", "app.venues.audit.model.StaffAuditEntry")
)
data class AuditEventWriteRequest(
    val occurredAt: Instant = Instant.now(),
    val actorType: AuditActorType = AuditActorType.STAFF,
    val actorId: UUID?,
    val action: String,
    val outcome: AuditOutcome,
    val subjectType: String? = null,
    val subjectId: String? = null,
    val venueId: UUID? = null,
    val organizationId: UUID? = null,
    val httpMethod: String? = null,
    val httpPath: String? = null,
    val httpStatus: Int? = null,
    val requestId: String? = null,
    val correlationId: String? = null,
    val clientIp: String? = null,
    val userAgent: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)
