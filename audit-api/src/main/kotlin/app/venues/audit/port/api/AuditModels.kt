package app.venues.audit.port.api

import java.time.Instant
import java.util.*

enum class AuditActorType {
    STAFF,
    SYSTEM
}

enum class AuditOutcome {
    SUCCESS,
    FAILURE
}

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
