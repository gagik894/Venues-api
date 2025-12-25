package app.venues.audit.service

import app.venues.audit.annotation.Auditable
import app.venues.audit.port.api.AuditActorType
import app.venues.audit.port.api.AuditEventWriteRequest
import app.venues.audit.port.api.AuditLogPort
import app.venues.audit.port.api.AuditOutcome
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.method.HandlerMethod
import java.util.*

@Component
class AuditLoggingFilter(
    private val auditLogPort: AuditLogPort
) : OncePerRequestFilter() {

    companion object {
        // Spring stores the matched handler under this request attribute.
        // We use the literal to stay compatible across Spring versions.
        private const val BEST_MATCHING_HANDLER_ATTR = "org.springframework.web.servlet.HandlerMapping.bestMatchingHandler"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        var status: Int? = null
        try {
            filterChain.doFilter(request, response)
            status = response.status
        } catch (ex: Exception) {
            status = response.status.takeIf { it > 0 } ?: 500
            // Re-throw to preserve default handling
            throw ex
        } finally {
            // Skip GET requests (read-only operations don't need HTTP method audits)
            if (request.method.equals("GET", ignoreCase = true)) {
                return
            }

            // Skip HTTP method audit if handler method has @Auditable annotation
            // (to avoid duplicate audits - the aspect will handle the business audit)
            val handler = request.getAttribute(BEST_MATCHING_HANDLER_ATTR)
            if (handler is HandlerMethod) {
                val auditable = handler.getMethodAnnotation(Auditable::class.java)
                if (auditable != null) {
                    // Handler has @Auditable, skip HTTP method audit to avoid duplicates
                    return
                }
            }

            val staffId = resolveUuid(request.getAttribute("staffId"))
            val userId = resolveUuid(request.getAttribute("userId"))
            val platformId = resolveUuid(request.getAttribute("platformId"))
            val actorType = when {
                staffId != null -> AuditActorType.STAFF
                userId != null -> AuditActorType.USER
                platformId != null -> AuditActorType.PLATFORM
                else -> AuditActorType.SYSTEM
            }
            val path = request.requestURI
            val action = "HTTP_${request.method.uppercase()}"

            val event = AuditEventWriteRequest(
                actorType = actorType,
                actorId = staffId ?: userId ?: platformId,
                action = action,
                outcome = if ((status ?: 500) < 400) AuditOutcome.SUCCESS else AuditOutcome.FAILURE,
                subjectType = "http",
                subjectId = path,
                venueId = resolveUuid(request.getAttribute("venueId")),
                organizationId = resolveUuid(request.getAttribute("organizationId")),
                httpMethod = request.method,
                httpPath = path,
                httpStatus = status,
                requestId = request.getHeader("X-Request-ID"),
                correlationId = request.getHeader("X-Correlation-ID"),
                clientIp = request.remoteAddr,
                userAgent = request.getHeader("User-Agent"),
                metadata = buildMetadata(request)
            )
            auditLogPort.write(event)
        }
    }

    private fun resolveUuid(value: Any?): UUID? {
        return when (value) {
            is UUID -> value
            is String -> runCatching { UUID.fromString(value) }.getOrNull()
            else -> null
        }
    }

    private fun buildMetadata(request: HttpServletRequest): Map<String, Any?> {
        val metadata = mutableMapOf<String, Any?>()
        if (!request.queryString.isNullOrBlank()) {
            metadata["query"] = request.queryString
        }
        return metadata
    }
}
