package app.venues.shared.web.context

import java.util.*

/**
 * ThreadLocal holder for venue domain resolution context.
 *
 * Populated by DomainContextFilter when X-Venue-Domain header is present.
 * Used by controllers and services to get the resolved venueId.
 */
object DomainContext {
    private val holder = ThreadLocal<ResolvedVenueDomain?>()

    fun get(): ResolvedVenueDomain? = holder.get()
    fun set(domain: ResolvedVenueDomain) = holder.set(domain)
    fun clear() = holder.remove()
}

/**
 * Resolved venue domain information.
 *
 * @property domain The original domain string from the header
 * @property venueId The resolved venue ID
 */
data class ResolvedVenueDomain(
    val domain: String,
    val venueId: UUID
)

