package app.venues.audit.service

import app.venues.audit.model.StaffAuditEntry
import app.venues.audit.persistence.domain.StaffAuditLogEntity
import app.venues.audit.persistence.repository.StaffAuditLogRepository
import app.venues.audit.port.api.StaffAuditPort
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Service implementation for government-grade staff audit logging.
 * Uses REQUIRES_NEW propagation to ensure audit writes succeed even if parent transaction fails.
 */
@Service
class StaffAuditLogService(
    private val auditLogRepository: StaffAuditLogRepository,
    private val objectMapper: ObjectMapper
) : StaffAuditPort {

    private val logger = KotlinLogging.logger {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun log(entry: StaffAuditEntry) {
        try {
            val entity = toEntity(entry)
            auditLogRepository.save(entity)

            if (logger.isDebugEnabled()) {
                logger.debug {
                    "Audit: staff=${entry.staffId} action=${entry.action} " +
                            "subject=${entry.subjectType}/${entry.subjectId} outcome=${entry.outcome}"
                }
            }
        } catch (e: Exception) {
            // Never fail the parent transaction due to audit write failure
            logger.error(e) {
                "Failed to write audit entry: staff=${entry.staffId} action=${entry.action} outcome=${entry.outcome}"
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun logAll(entries: List<StaffAuditEntry>) {
        if (entries.isEmpty()) return

        try {
            val entities = entries.map { toEntity(it) }
            auditLogRepository.saveAll(entities)

            if (logger.isDebugEnabled()) {
                logger.debug { "Audit: logged ${entries.size} entries" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to write ${entries.size} audit entries" }
        }
    }

    private fun toEntity(entry: StaffAuditEntry): StaffAuditLogEntity {
        return StaffAuditLogEntity(
            occurredAt = entry.occurredAt,
            staffId = entry.staffId,
            platformId = entry.platformId,
            venueId = entry.venueId,
            organizationId = entry.organizationId,
            action = entry.action.name,
            category = entry.category.name,
            severity = entry.severity.name,
            subjectType = entry.subjectType,
            subjectId = entry.subjectId,
            description = entry.description ?: generateDescription(entry),
            changesJson = entry.changes?.let { serializeChanges(it) },
            outcome = entry.outcome.name,
            failureReason = entry.failureReason,
            clientIp = entry.clientIp,
            userAgent = entry.userAgent,
            metadataJson = objectMapper.writeValueAsString(entry.metadata)
        )
    }

    /**
     * Generate a human-readable description from action template and metadata.
     */
    private fun generateDescription(entry: StaffAuditEntry): String? {
        val template = entry.action.descriptionTemplate ?: return null

        // Simple template substitution: replace {key} with metadata values
        var result = template
        entry.metadata.forEach { (key, value) ->
            result = result.replace("{$key}", value?.toString() ?: "")
        }

        // Clean up any unreplaced placeholders
        result = result.replace(Regex("\\{[^}]+}"), "")

        return result.trim().ifEmpty { null }
    }

    private fun serializeChanges(changes: Map<String, app.venues.audit.model.FieldChange>): String {
        val changesMap = changes.mapValues { (_, change) ->
            mapOf("old" to change.old, "new" to change.new)
        }
        return objectMapper.writeValueAsString(changesMap)
    }
}
