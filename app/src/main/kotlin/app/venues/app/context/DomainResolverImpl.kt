package app.venues.app.context

import app.venues.shared.web.context.DomainResolver
import app.venues.shared.web.context.ResolvedVenueDomain
import app.venues.venue.api.VenueApi
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Implementation of DomainResolver port.
 *
 * Located in app module because it requires access to VenueApi
 * which is defined in the venue-api module.
 *
 * Uses Caffeine caching to avoid repeated database lookups for the same domain.
 * Cache entries expire after 10 minutes.
 */
@Service
class DomainResolverImpl(
    private val venueApi: VenueApi
) : DomainResolver {

    private val logger = KotlinLogging.logger {}

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build<String, ResolvedVenueDomain?>()

    override fun resolve(domain: String): ResolvedVenueDomain? {
        val canonical = canonicalize(domain) ?: run {
            logger.debug { "Domain header malformed or empty; skipping resolution" }
            return null
        }

        return cache.get(canonical) { key ->
            resolveFromDatabase(key)
        }
    }

    override fun invalidate(domain: String) {
        val canonical = canonicalize(domain) ?: run {
            logger.debug { "Skipping cache invalidation for malformed domain: $domain" }
            return
        }
        cache.invalidate(canonical)
        logger.debug { "Invalidated domain cache for: $canonical" }
    }

    override fun invalidateAll() {
        cache.invalidateAll()
        logger.info { "Invalidated all domain cache entries" }
    }

    private fun resolveFromDatabase(domain: String): ResolvedVenueDomain? {
        logger.debug { "Resolving domain from database: $domain" }

        val venue = venueApi.getVenueByDomain(domain)
        if (venue != null) {
            logger.debug { "Domain $domain resolved to Venue: ${venue.id}" }
            return ResolvedVenueDomain(
                domain = domain,
                venueId = venue.id
            )
        }

        logger.debug { "Domain $domain could not be resolved to any venue" }
        return null
    }

    /**
     * Normalizes a domain for stable cache keys and DB lookups.
     * - trims, lowercases
     * - strips protocol, port, and trailing slash
     * - rejects invalid characters
     */
    private fun canonicalize(raw: String): String? {
        if (raw.isBlank()) return null

        val trimmed = raw.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
        val withoutPath = trimmed.substringBefore("/")
        val hostOnly = withoutPath.substringBefore(":")
        if (hostOnly.isBlank()) return null

        val host = hostOnly.trimEnd('.')
        val isValid = HOST_REGEX.matches(host)
        return if (isValid) host else null
    }

    companion object {
        private val HOST_REGEX = Regex("^[a-z0-9.-]{1,253}$")
    }
}

