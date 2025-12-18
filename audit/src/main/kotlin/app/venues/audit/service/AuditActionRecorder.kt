package app.venues.audit.service

import app.venues.audit.port.api.AuditActorType
import app.venues.audit.port.api.AuditEventWriteRequest
import app.venues.audit.port.api.AuditLogPort
import app.venues.audit.port.api.AuditOutcome
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class AuditActionRecorder(
    private val auditLogPort: AuditLogPort
) {
    fun success(
        action: String,
        staffId: UUID?,
        venueId: UUID?,
        subjectType: String,
        subjectId: String?,
        organizationId: UUID? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        write(action, staffId, venueId, subjectType, subjectId, organizationId, metadata, AuditOutcome.SUCCESS)
    }

    fun failure(
        action: String,
        staffId: UUID?,
        venueId: UUID?,
        subjectType: String,
        subjectId: String?,
        organizationId: UUID? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        write(action, staffId, venueId, subjectType, subjectId, organizationId, metadata, AuditOutcome.FAILURE)
    }

    private fun write(
        action: String,
        staffId: UUID?,
        venueId: UUID?,
        subjectType: String,
        subjectId: String?,
        organizationId: UUID?,
        metadata: Map<String, Any?>,
        outcome: AuditOutcome
    ) {
        auditLogPort.write(
            AuditEventWriteRequest(
                occurredAt = Instant.now(),
                actorType = if (resolveActorId(staffId) != null) AuditActorType.STAFF else AuditActorType.SYSTEM,
                actorId = resolveActorId(staffId),
                action = action,
                outcome = outcome,
                subjectType = subjectType,
                subjectId = subjectId,
                venueId = venueId,
                organizationId = organizationId,
                metadata = metadata
            )
        )
    }

    private fun resolveActorId(explicitId: UUID?): UUID? {
        if (explicitId != null) return explicitId
        val auth = SecurityContextHolder.getContext().authentication
        val principal = auth?.principal
        return when (principal) {
            is Map<*, *> -> principal["id"] as? UUID
                ?: (principal["id"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            is UUID -> principal
            is String -> runCatching { UUID.fromString(principal) }.getOrNull()
            else -> null
        }
    }
}
