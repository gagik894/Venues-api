package app.venues.shared.web.filter

import app.venues.shared.web.context.DomainContext
import app.venues.shared.web.context.DomainResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter that reads X-Venue-Domain header and populates DomainContext.
 *
 * This filter runs before JwtAuthenticationFilter and makes the resolved
 * venue information available to controllers and services via ThreadLocal.
 *
 * Header: X-Venue-Domain: opera.am
 *
 * After this filter runs:
 * - DomainContext.get() returns ResolvedVenueDomain with venueId
 * - Request attribute "venueId" contains the UUID
 *
 * Architecture Note:
 * - This filter depends on DomainResolver interface (Port)
 * - Implementation is provided by app module (DomainResolverImpl)
 */
@Component
class DomainContextFilter(
    private val domainResolver: DomainResolver
) : OncePerRequestFilter() {

    private val log = KotlinLogging.logger {}

    companion object {
        const val DOMAIN_HEADER = "X-Venue-Domain"
        const val VENUE_ID_ATTRIBUTE = "venueId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val domain = request.getHeader(DOMAIN_HEADER)

            if (!domain.isNullOrBlank()) {
                log.debug { "Domain header found: $domain" }

                val resolved = domainResolver.resolve(domain)
                if (resolved != null) {
                    DomainContext.set(resolved)
                    request.setAttribute(VENUE_ID_ATTRIBUTE, resolved.venueId)
                    log.debug { "Domain context set: venueId=${resolved.venueId}" }
                } else {
                    log.debug { "Domain $domain could not be resolved to a venue" }
                }
            }

            filterChain.doFilter(request, response)
        } finally {
            // Always clear ThreadLocal to prevent memory leaks
            DomainContext.clear()
        }
    }
}

