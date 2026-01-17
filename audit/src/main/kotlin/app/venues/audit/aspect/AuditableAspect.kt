package app.venues.audit.aspect

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.audit.model.AuditAction
import app.venues.audit.model.AuditSeverity
import app.venues.audit.model.StaffAuditEntry
import app.venues.audit.port.api.StaffAuditPort
import app.venues.common.model.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.kotlinFunction

/**
 * Aspect that automatically records staff audit entries for methods annotated with @Auditable.
 *
 * Records to staff_audit_log with:
 * - Staff identity (from @RequestAttribute staffId)
 * - Action (mapped to AuditAction enum)
 * - Subject (type and ID)
 * - Outcome (SUCCESS/FAILURE)
 * - Human-readable description
 * - Metadata (from request/response)
 */
@Aspect
@Component
class AuditableAspect(
    private val staffAuditPort: StaffAuditPort
) {
    private val logger = KotlinLogging.logger {}

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    fun auditSuccess(joinPoint: JoinPoint, auditable: Auditable, result: Any?) {
        try {
            val context = extractAuditContext(joinPoint, auditable)

            // Skip if no staff ID (can't audit anonymous actions in staff audit log)
            val staffId = context.staffId
            if (staffId == null) {
                logger.debug { "Skipping audit for ${auditable.action}: no staffId available" }
                return
            }

            val action = AuditAction.fromString(auditable.action)
            // Prefer path variable (for updates/deletes), fall back to result (for creates)
            val subjectId = context.pathVariableSubjectId
                ?: extractSubjectIdFromResult(result, auditable.subjectType)

            val metadata = enrichMetadata(joinPoint, context.metadata, result)
            val description = buildDescription(auditable, action, metadata, result)

            val entry = StaffAuditEntry.builder(staffId, action)
                .venueId(context.venueId)
                .organizationId(context.organizationId)
                .subject(auditable.subjectType, subjectId)
                .description(description)
                .success()
                .clientIp(getClientIp())
                .userAgent(getUserAgent())
                .metadata(metadata)
                .apply {
                    // Override severity if specified in annotation
                    if (auditable.severity.isNotBlank()) {
                        try {
                            severity(AuditSeverity.valueOf(auditable.severity.uppercase()))
                        } catch (_: IllegalArgumentException) {
                            // Keep action default
                        }
                    }
                }
                .build()

            staffAuditPort.log(entry)

            logger.debug {
                "Audit [SUCCESS] staff=$staffId action=${action.name} " +
                        "subject=${auditable.subjectType}/$subjectId"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to record audit for ${auditable.action}" }
        }
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "exception")
    fun auditFailure(joinPoint: JoinPoint, auditable: Auditable, exception: Exception) {
        try {
            val context = extractAuditContext(joinPoint, auditable)

            val staffId = context.staffId
            if (staffId == null) {
                logger.debug { "Skipping failure audit for ${auditable.action}: no staffId available" }
                return
            }

            val action = AuditAction.fromString(auditable.action)

            val entry = StaffAuditEntry.builder(staffId, action)
                .venueId(context.venueId)
                .organizationId(context.organizationId)
                .subject(auditable.subjectType, context.pathVariableSubjectId)
                .description("${action.descriptionTemplate ?: auditable.action} failed: ${exception.message}")
                .failure(exception.message ?: exception.javaClass.simpleName)
                .clientIp(getClientIp())
                .userAgent(getUserAgent())
                .metadata(context.metadata)
                .metadata("errorType", exception.javaClass.simpleName)
                .apply {
                    if (auditable.severity.isNotBlank()) {
                        try {
                            severity(AuditSeverity.valueOf(auditable.severity.uppercase()))
                        } catch (_: IllegalArgumentException) {
                        }
                    }
                }
                .build()

            staffAuditPort.log(entry)

            logger.debug {
                "Audit [FAILURE] staff=$staffId action=${action.name} error=${exception.javaClass.simpleName}"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to record failure audit for ${auditable.action}" }
        }
    }

    // =========================================================================
    // Context Extraction
    // =========================================================================

    private data class AuditContext(
        val staffId: UUID?,
        val venueId: UUID?,
        val organizationId: UUID?,
        val pathVariableSubjectId: String?,
        val metadata: Map<String, Any?> = emptyMap()
    )

    private fun extractAuditContext(joinPoint: JoinPoint, auditable: Auditable): AuditContext {
        val method = (joinPoint.signature as? org.aspectj.lang.reflect.MethodSignature)?.method
            ?: return AuditContext(null, null, null, null)

        val args = joinPoint.args

        val staffId = extractRequestAttribute(method, args, "staffId", UUID::class.java)

        val venueId = if (auditable.includeVenueId) {
            extractPathVariable(method, args, "venueId", UUID::class.java)
        } else null

        val organizationId = if (auditable.includeOrganizationId) {
            extractPathVariable(method, args, "organizationId", UUID::class.java)
        } else null

        // Try to extract a subject ID from path variables based on subjectType
        val subjectIdFromPath = when (auditable.subjectType.lowercase()) {
            "event" -> extractPathVariable(method, args, "eventId", UUID::class.java)?.toString()
            "session", "event_session" -> extractPathVariable(method, args, "sessionId", UUID::class.java)?.toString()
            "venue" -> extractPathVariable(method, args, "venueId", UUID::class.java)?.toString()
            "booking" -> extractPathVariable(method, args, "bookingId", UUID::class.java)?.toString()
            "ticket" -> extractPathVariable(method, args, "ticketId", UUID::class.java)?.toString()
            "chart", "seating_chart" -> extractPathVariable(method, args, "chartId", UUID::class.java)?.toString()
            "platform" -> extractPathVariable(method, args, "platformId", UUID::class.java)?.toString()
            "promo", "promo_code" -> extractPathVariable(method, args, "promoId", UUID::class.java)?.toString()
            "organization" -> extractPathVariable(method, args, "organizationId", UUID::class.java)?.toString()
            "staff" -> extractPathVariable(method, args, "targetStaffId", UUID::class.java)?.toString()
            else -> null
        }

        val metadata = extractMetadata(method, args)

        return AuditContext(staffId, venueId, organizationId, subjectIdFromPath, metadata)
    }

    private fun <T> extractRequestAttribute(method: Method, args: Array<Any>, attrName: String, clazz: Class<T>): T? {
        val kParams = method.kotlinFunction?.parameters
        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val requestAttr = param.getAnnotation(RequestAttribute::class.java)
            if (requestAttr != null) {
                val nameMatches = (requestAttr.value == attrName || requestAttr.name == attrName)
                val paramNameMatches = kParams?.getOrNull(index + 1)?.name == attrName
                val typeMatches = param.type == clazz
                if (nameMatches || paramNameMatches || typeMatches) {
                    try {
                        clazz.cast(args.getOrNull(index))
                    } catch (e: Exception) {
                        null
                    }
                } else null
            } else null
        }
    }

    private fun <T> extractPathVariable(method: Method, args: Array<Any>, varName: String, clazz: Class<T>): T? {
        val kParams = method.kotlinFunction?.parameters
        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val pathVar = param.getAnnotation(PathVariable::class.java)
            if (pathVar != null) {
                val nameMatches = (pathVar.value == varName || pathVar.name == varName)
                val paramNameMatches = kParams?.getOrNull(index + 1)?.name == varName
                if (nameMatches || paramNameMatches) {
                    try {
                        clazz.cast(args.getOrNull(index))
                    } catch (e: Exception) {
                        null
                    }
                } else null
            } else null
        }
    }

    private fun extractMetadata(method: Method, args: Array<Any>): Map<String, Any?> {
        val metadata = mutableMapOf<String, Any?>()
        method.parameters.indices.forEach { index ->
            val auditMeta = method.parameters[index].getAnnotation(AuditMetadata::class.java)
            if (auditMeta != null && index < args.size) {
                metadata[auditMeta.value] = args[index]
            }
        }
        return metadata
    }

    // =========================================================================
    // Metadata Enrichment
    // =========================================================================

    private fun enrichMetadata(joinPoint: JoinPoint, baseMetadata: Map<String, Any?>, result: Any?): Map<String, Any?> {
        val method = (joinPoint.signature as? org.aspectj.lang.reflect.MethodSignature)?.method ?: return baseMetadata
        val args = joinPoint.args
        val enriched = baseMetadata.toMutableMap()

        // Extract common path variables
        extractPathVariable(method, args, "eventId", UUID::class.java)?.let { enriched["eventId"] = it.toString() }
        extractPathVariable(method, args, "sessionId", UUID::class.java)?.let { enriched["sessionId"] = it.toString() }
        extractPathVariable(method, args, "bookingId", UUID::class.java)?.let { enriched["bookingId"] = it.toString() }

        // Extract key fields from request body
        val requestObj = enriched["request"]
        if (requestObj != null) {
            extractRequestFields(requestObj, enriched)
        }

        // Extract key fields from result
        if (result is ApiResponse<*>) {
            val data = result.data
            if (data != null) {
                when (data) {
                    is Int, is Long -> enriched["affectedCount"] = data
                    else -> extractResultFields(data, enriched)
                }
            }
        } else if (result != null) {
            // Handle unwrapped primitives
            when (result) {
                is Int, is Long -> enriched["affectedCount"] = result
                else -> extractResultFields(result, enriched)
            }
        }

        // Remove the full request object from metadata (too verbose)
        enriched.remove("request")

        return enriched
    }

    private fun extractRequestFields(obj: Any, target: MutableMap<String, Any?>) {
        try {
            val kClass = obj::class
            val props = kClass.memberProperties.associateBy { it.name }

            // Key identifiers
            props["templateId"]?.getter?.call(obj)?.let { target["templateId"] = it.toString() }
            props["eventId"]?.getter?.call(obj)?.let { target["eventId"] = it.toString() }
            props["sessionId"]?.getter?.call(obj)?.let { target["sessionId"] = it.toString() }

            // Business fields
            props["status"]?.getter?.call(obj)?.let { target["targetStatus"] = it.toString() }
            props["name"]?.getter?.call(obj)?.let { target["name"] = it.toString().take(100) }
            props["code"]?.getter?.call(obj)?.let { target["code"] = it.toString() }
            props["email"]?.getter?.call(obj)?.let { target["email"] = it.toString() }
            props["quantity"]?.getter?.call(obj)?.let { target["quantity"] = it }
            props["reason"]?.getter?.call(obj)?.let { target["reason"] = it.toString().take(100) }

            // Collection counts (not full lists)
            props["seatIds"]?.getter?.call(obj)
                ?.let { (it as? Collection<*>)?.size?.let { c -> target["seatCount"] = c } }
            props["tableIds"]?.getter?.call(obj)
                ?.let { (it as? Collection<*>)?.size?.let { c -> target["tableCount"] = c } }
            props["gaIds"]?.getter?.call(obj)
                ?.let { (it as? Collection<*>)?.size?.let { c -> target["gaCount"] = c } }
        } catch (_: Exception) {
        }
    }

    private fun extractResultFields(data: Any, target: MutableMap<String, Any?>) {
        try {
            val kClass = data::class
            val props = kClass.memberProperties.associateBy { it.name }

            props["id"]?.getter?.call(data)?.let { target["resultId"] = it.toString() }
            props["status"]?.getter?.call(data)?.let { target["resultStatus"] = it.toString() }
            props["bookingId"]?.getter?.call(data)?.let { target["bookingId"] = it.toString() }
            props["token"]?.getter?.call(data)?.let { target["token"] = it.toString() }
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // Description Building
    // =========================================================================

    private fun buildDescription(
        auditable: Auditable,
        action: AuditAction,
        metadata: Map<String, Any?>,
        result: Any?
    ): String {
        // Use custom template if provided
        val template = auditable.descriptionTemplate.takeIf { it.isNotBlank() }
            ?: action.descriptionTemplate
            ?: return action.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

        // Substitute placeholders
        var description = template
        metadata.forEach { (key, value) ->
            description = description.replace("{$key}", value?.toString() ?: "")
        }

        // Try to get name from result for better descriptions
        if (result is ApiResponse<*> && result.data != null) {
            try {
                val data = result.data!!
                val props = data::class.memberProperties.associateBy { it.name }
                props["name"]?.getter?.call(data)
                    ?.let { description = description.replace("{eventName}", it.toString()) }
                props["name"]?.getter?.call(data)
                    ?.let { description = description.replace("{venueName}", it.toString()) }
                props["code"]?.getter?.call(data)
                    ?.let { description = description.replace("{promoCode}", it.toString()) }
            } catch (_: Exception) {
            }
        }

        // Clean up unreplaced placeholders
        description = description.replace(Regex("\\{[^}]+}"), "").trim()

        return description.ifEmpty { action.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() } }
    }

    private fun extractSubjectIdFromResult(result: Any?, subjectType: String): String? {
        if (result !is ApiResponse<*>) return null
        val data = result.data ?: return null

        return when (data) {
            is UUID -> data.toString()
            is String -> data
            else -> {
                try {
                    val kClass = data::class
                    val idProperty =
                        kClass.memberProperties.find { it.name == "id" || it.name == "code" || it.name == "slug" }
                    idProperty?.getter?.call(data)?.toString()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    // =========================================================================
    // HTTP Context
    // =========================================================================

    private fun getClientIp(): String? {
        return try {
            val request = getCurrentRequest()
            request?.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                ?: request?.remoteAddr
        } catch (_: Exception) {
            null
        }
    }

    private fun getUserAgent(): String? {
        return try {
            getCurrentRequest()?.getHeader("User-Agent")?.take(512)
        } catch (_: Exception) {
            null
        }
    }

    private fun getCurrentRequest(): HttpServletRequest? {
        return try {
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        } catch (_: Exception) {
            null
        }
    }
}
