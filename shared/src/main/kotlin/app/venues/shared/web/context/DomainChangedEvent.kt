package app.venues.shared.web.context

import java.util.*

/**
 * Published when a venue's custom domain changes.
 * Listeners should invalidate domain resolution caches.
 */
data class DomainChangedEvent(
    val venueId: UUID,
    val oldDomain: String?,
    val newDomain: String?
)
