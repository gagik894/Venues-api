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
        val resolvedActorId = resolveActorId(staffId)
        val resolvedActorType = resolveActorType(explicitStaffId = staffId)
        auditLogPort.write(
            AuditEventWriteRequest(
                occurredAt = Instant.now(),
                actorType = resolvedActorType,
                actorId = resolvedActorId,
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

    private fun resolveActorType(explicitStaffId: UUID?): AuditActorType {
        // Explicit staffId always implies STAFF
        if (explicitStaffId != null) return AuditActorType.STAFF

        val auth = SecurityContextHolder.getContext().authentication
        val principal = auth?.principal

        val roleFromPrincipal = (principal as? Map<*, *>)?.get("role")?.toString()?.uppercase()
        return when (roleFromPrincipal) {
            "USER" -> AuditActorType.USER
            "STAFF", "SUPER_ADMIN" -> AuditActorType.STAFF
            "PLATFORM" -> AuditActorType.PLATFORM
            else -> if (resolveActorId(null) != null) AuditActorType.STAFF else AuditActorType.SYSTEM
        }
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
