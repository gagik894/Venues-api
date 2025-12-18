package app.venues.audit.service

import app.venues.audit.persistence.domain.AuditEventEntity
import app.venues.audit.persistence.repository.AuditEventRepository
import app.venues.audit.port.api.AuditEventWriteRequest
import app.venues.audit.port.api.AuditLogPort
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class AuditLogService(
    private val auditEventRepository: AuditEventRepository,
    private val objectMapper: ObjectMapper
) : AuditLogPort {

    private val logger = KotlinLogging.logger {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun write(event: AuditEventWriteRequest) {
        try {
            val entity = AuditEventEntity(
                occurredAt = event.occurredAt,
                actorType = event.actorType.name,
                actorId = event.actorId,
                action = event.action,
                outcome = event.outcome.name,
                subjectType = event.subjectType,
                subjectId = event.subjectId,
                venueId = event.venueId,
                organizationId = event.organizationId,
                httpMethod = event.httpMethod,
                httpPath = event.httpPath,
                httpStatus = event.httpStatus,
                requestId = event.requestId,
                correlationId = event.correlationId,
                clientIp = event.clientIp,
                userAgent = event.userAgent,
                metadataJson = objectMapper.writeValueAsString(event.metadata)
            )
            auditEventRepository.save(entity)
        } catch (e: Exception) {
            logger.error(e) { "Failed to persist audit event action=${event.action} outcome=${event.outcome}" }
        }
    }
}
