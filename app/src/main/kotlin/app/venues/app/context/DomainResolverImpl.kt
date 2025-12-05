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
        return cache.get(domain) { key ->
            resolveFromDatabase(key)
        }
    }

    override fun invalidate(domain: String) {
        cache.invalidate(domain)
        logger.debug { "Invalidated domain cache for: $domain" }
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
}

