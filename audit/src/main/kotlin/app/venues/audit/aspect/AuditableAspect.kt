package app.venues.audit.aspect

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.audit.service.AuditActionRecorder
import app.venues.common.model.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.kotlinFunction

/**
 * Aspect that automatically records audit events for methods annotated with @Auditable.
 *
 * Responsibilities:
 * 1. Intercept controller methods annotated with @Auditable
 * 2. Extract staffId from @RequestAttribute("staffId") or SecurityContext
 * 3. Extract venueId/organizationId from @PathVariable if specified
 * 4. Extract subject ID from method result (if ApiResponse)
 * 5. Call auditActionRecorder.success() or .failure() appropriately
 */
@Aspect
@Component
class AuditableAspect(
    private val auditActionRecorder: AuditActionRecorder
) {
    private val logger = KotlinLogging.logger {}

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    fun auditSuccess(joinPoint: JoinPoint, auditable: Auditable, result: Any?) {
        try {
            val (staffId, venueId, organizationId, baseMetadata) = extractAuditContext(
                joinPoint,
                auditable
            )

            val subjectIdFromResult = extractSubjectIdFromResult(result, auditable.subjectType)

            val method = (joinPoint.signature as? org.aspectj.lang.reflect.MethodSignature)?.method
            val args = joinPoint.args
            val eventIdVar = extractPathVariable(method!!, args, "eventId", UUID::class.java)
            val sessionIdVar = extractPathVariable(method, args, "sessionId", UUID::class.java)
            // Choose subjectId based on subjectType semantics
            val subjectId = when (auditable.subjectType.lowercase()) {
                "session", "event_session" -> sessionIdVar?.toString() ?: subjectIdFromResult ?: eventIdVar?.toString()
                "event" -> eventIdVar?.toString() ?: subjectIdFromResult
                else -> subjectIdFromResult ?: sessionIdVar?.toString() ?: eventIdVar?.toString()
            }

            val enrichedMetadata = enrichMetadata(joinPoint, baseMetadata, result)

            auditActionRecorder.success(
                action = auditable.action,
                staffId = staffId,
                venueId = venueId,
                subjectType = auditable.subjectType,
                subjectId = subjectId,
                organizationId = organizationId,
                metadata = enrichedMetadata
            )

            logger.debug {
                "Audit recorded [SUCCESS] action=${auditable.action} " +
                        "subjectType=${auditable.subjectType} subjectId=$subjectId"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to record audit for ${auditable.action}" }
        }
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "exception")
    fun auditFailure(joinPoint: JoinPoint, auditable: Auditable, exception: Exception) {
        try {
            val (staffId, venueId, organizationId, metadata) = extractAuditContext(
                joinPoint,
                auditable
            )

            val failureMetadata = metadata.toMutableMap().apply {
                put("errorType", exception.javaClass.simpleName)
                put("errorMessage", exception.message ?: "Unknown error")
            }

            auditActionRecorder.failure(
                action = auditable.action,
                staffId = staffId,
                venueId = venueId,
                subjectType = auditable.subjectType,
                subjectId = null,
                organizationId = organizationId,
                metadata = failureMetadata
            )

            logger.debug {
                "Audit recorded [FAILURE] action=${auditable.action} " +
                        "error=${exception.javaClass.simpleName}"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to record audit failure for ${auditable.action}" }
        }
    }

    private fun enrichMetadata(joinPoint: JoinPoint, metadata: Map<String, Any?>, result: Any?): Map<String, Any?> {
        val method = (joinPoint.signature as? org.aspectj.lang.reflect.MethodSignature)?.method
        val args = joinPoint.args
        val enriched = metadata.toMutableMap()

        // Include common path variables when present
        val eventId = extractPathVariable(method!!, args, "eventId", UUID::class.java)
        val sessionId = extractPathVariable(method, args, "sessionId", UUID::class.java)
        if (eventId != null) enriched["eventId"] = eventId.toString()
        if (sessionId != null) enriched["sessionId"] = sessionId.toString()

        // Expand request object metadata into expected audit keys
        val requestObj = enriched["request"]
        if (requestObj != null) {
            try {
                val kClass = requestObj::class
                val props = kClass.memberProperties.associateBy { it.name }
                props["templateId"]?.getter?.call(requestObj)?.let { enriched["templateId"] = it.toString() }
                props["seatIds"]?.getter?.call(requestObj)?.let { list ->
                    enriched["seatIds"] = list
                    val count = (list as? Collection<*>)?.size ?: 0
                    enriched["seatIdCount"] = count
                }
                props["tableIds"]?.getter?.call(requestObj)?.let { list ->
                    enriched["tableIds"] = list
                    val count = (list as? Collection<*>)?.size ?: 0
                    enriched["tableIdCount"] = count
                }
                // GA areas may be named gaIds or gaAreaIds depending on DTOs
                (props["gaIds"] ?: props["gaAreaIds"])?.getter?.call(requestObj)?.let { list ->
                    val count = (list as? Collection<*>)?.size ?: 0
                    enriched["gaAreaIdCount"] = count
                }
                props["status"]?.getter?.call(requestObj)?.let { status ->
                    enriched["targetStatus"] = status.toString()
                }
                props["reason"]?.getter?.call(requestObj)?.let { reason ->
                    enriched["reason"] = reason
                }
            } catch (_: Exception) {
                // best-effort enrichment
            }
        }

        // Enrich from result: affectedCount and current status
        if (result is ApiResponse<*>) {
            val data = result.data
            when (data) {
                is Int -> enriched["affectedCount"] = data
                else -> {
                    try {
                        val kClass = data?.let { it::class }
                        val statusProp = kClass?.memberProperties?.find { it.name == "status" }
                        statusProp?.getter?.call(data)?.let { enriched["status"] = it.toString() }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return enriched
    }

    private data class AuditContext(
        val staffId: UUID?,
        val venueId: UUID?,
        val organizationId: UUID?,
        val metadata: Map<String, Any?> = emptyMap()
    )

    private fun extractAuditContext(
        joinPoint: JoinPoint,
        auditable: Auditable
    ): AuditContext {
        val method = (joinPoint.signature as? org.aspectj.lang.reflect.MethodSignature)?.method
            ?: return AuditContext(null, null, null)

        val args = joinPoint.args

        val staffId = extractRequestAttribute(method, args, "staffId", UUID::class.java)
        val venueId = if (auditable.includeVenueId) {
            extractPathVariable(method, args, "venueId", UUID::class.java)
        } else null

        val organizationId = if (auditable.includeOrganizationId) {
            extractPathVariable(method, args, "organizationId", UUID::class.java)
        } else null

        val metadata = extractMetadata(method, args)

        return AuditContext(staffId, venueId, organizationId, metadata)
    }

    private fun <T> extractRequestAttribute(
        method: Method,
        args: Array<Any>,
        attrName: String,
        clazz: Class<T>
    ): T? {
        val kParams = method.kotlinFunction?.parameters
        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val requestAttr = param.getAnnotation(RequestAttribute::class.java)
            if (requestAttr != null) {
                val nameMatches = (requestAttr.value == attrName || requestAttr.name == attrName)
                val paramNameMatches = kParams?.getOrNull(index + 1)?.name == attrName // +1 skips the implicit 'this'
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

    private fun <T> extractPathVariable(
        method: Method,
        args: Array<Any>,
        varName: String,
        clazz: Class<T>
    ): T? {
        val kParams = method.kotlinFunction?.parameters
        return method.parameters.indices.firstNotNullOfOrNull { index ->
            val param = method.parameters[index]
            val pathVar = param.getAnnotation(PathVariable::class.java)
            if (pathVar != null) {
                val nameMatches = (pathVar.value == varName || pathVar.name == varName)
                val paramNameMatches = kParams?.getOrNull(index + 1)?.name == varName // +1 skips the implicit 'this'
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

    private fun extractMetadata(
        method: Method,
        args: Array<Any>
    ): Map<String, Any?> {
        val metadata = mutableMapOf<String, Any?>()

        method.parameters.indices.forEach { index ->
            val auditMeta = method.parameters[index].getAnnotation(AuditMetadata::class.java)
            if (auditMeta != null && index < args.size) {
                metadata[auditMeta.value] = args[index]
            }
        }

        return metadata
    }

    private fun extractSubjectIdFromResult(result: Any?, subjectType: String): String? {
        return when {
            result is ApiResponse<*> -> {
                val data = result.data
                when {
                    data is UUID -> data.toString()
                    data is String -> data
                    data != null -> {
                        try {
                            val kClass = data::class
                            val idProperty = kClass.memberProperties.find {
                                it.name == "id" || it.name == "code" || it.name == "slug"
                            }
                            val idValue = idProperty?.getter?.call(data)
                            idValue?.toString()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    else -> null
                }
            }

            else -> null
        }
    }
}
