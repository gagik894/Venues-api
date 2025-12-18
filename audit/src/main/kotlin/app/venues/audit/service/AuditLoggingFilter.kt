package app.venues.audit.service

import app.venues.audit.port.api.AuditActorType
import app.venues.audit.port.api.AuditEventWriteRequest
import app.venues.audit.port.api.AuditLogPort
import app.venues.audit.port.api.AuditOutcome
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

@Component
class AuditLoggingFilter(
    private val auditLogPort: AuditLogPort
) : OncePerRequestFilter() {

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
            val staffId = resolveUuid(request.getAttribute("staffId"))
            val actorType = if (staffId != null) AuditActorType.STAFF else AuditActorType.SYSTEM
            val path = request.requestURI
            val action = "HTTP_${request.method.uppercase()}"

            val event = AuditEventWriteRequest(
                actorType = actorType,
                actorId = staffId,
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
