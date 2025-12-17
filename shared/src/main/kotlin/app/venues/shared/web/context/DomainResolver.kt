package app.venues.shared.web.context

/**
 * Port interface for venue domain resolution.
 *
 * Implementations are provided by the app module which has access
 * to VenueApi.
 *
 * This follows the Hexagonal Architecture pattern:
 * - Interface (Port) defined in shared module
 * - Implementation (Adapter) provided in app module
 */
interface DomainResolver {

    /**
     * Resolves a custom domain to a Venue.
     *
     * @param domain The custom domain (e.g., "opera.am")
     * @return ResolvedVenueDomain with venue info, or null if domain not found
     */
    fun resolve(domain: String): ResolvedVenueDomain?

    /**
     * Invalidates the cache entry for a specific domain.
     * Call this when a venue's custom domain is updated.
     */
    fun invalidate(domain: String)

    /**
     * Invalidates all cached domain resolutions.
     */
    fun invalidateAll()
}

