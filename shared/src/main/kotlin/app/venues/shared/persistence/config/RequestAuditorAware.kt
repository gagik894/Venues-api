package app.venues.shared.persistence.config

import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.*

/**
 * Resolves current auditor (staff/user UUID) from request attributes or SecurityContext.
 */
class RequestAuditorAware : AuditorAware<UUID> {
    override fun getCurrentAuditor(): Optional<UUID> {
        // Prefer request attribute set by JwtAuthenticationFilter
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val attrId = request?.getAttribute("staffId") as? UUID
            ?: request?.getAttribute("userId") as? UUID
        if (attrId != null) {
            return Optional.of(attrId)
        }

        // Fallback to SecurityContext principal
        val auth = SecurityContextHolder.getContext().authentication
        val principal = auth?.principal
        val id = when (principal) {
            is Map<*, *> -> (principal["id"] as? UUID)
            is UUID -> principal
            is String -> runCatching { UUID.fromString(principal) }.getOrNull()
            else -> null
        }
        return Optional.ofNullable(id)
    }
}
