package app.venues.event.service

import app.venues.event.domain.Event
import app.venues.event.domain.EventStatus
import app.venues.shared.web.revalidation.RevalidationNotifier
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.*

@Service
class EventRevalidationService(
    private val revalidationNotifier: RevalidationNotifier,
    private val venueApi: VenueApi
) {

    private val logger = KotlinLogging.logger {}
    private val defaultLanguages = setOf("en", "hy", "ru")

    fun onEventUpdated(event: Event, includeDetail: Boolean, reason: String, force: Boolean = false) {
        if (!force && event.status != EventStatus.PUBLISHED) {
            return
        }

        val domain = resolveDomain(event.venueId) ?: return
        val paths = buildPaths(event, includeDetail)
        if (paths.isEmpty()) return

        runAfterCommit {
            revalidationNotifier.revalidate(domain, paths, reason)
        }
    }

    fun onPublishFromDraft(event: Event) {
        // Draft -> Published: rebuild listings/home only; page will build on first visit.
        onEventUpdated(event, includeDetail = false, reason = "event-published", force = true)
    }

    fun onRepublish(event: Event) {
        // Suspended -> Published: rebuild listings and detail.
        onEventUpdated(event, includeDetail = true, reason = "event-republished", force = true)
    }

    fun onUnpublish(event: Event, reason: String) {
        onEventUpdated(event, includeDetail = true, reason = reason, force = true)
    }

    private fun resolveDomain(venueId: UUID): String? {
        val venueInfo = venueApi.getVenueBasicInfo(venueId)
        val domain = venueInfo?.customDomain
        if (domain.isNullOrBlank()) {
            logger.debug { "Skipping revalidation: venue domain missing for venue=$venueId" }
            return null
        }
        return domain
    }

    private fun buildPaths(event: Event, includeDetail: Boolean): List<String> {
        val languages = resolveLanguages(event)
        val paths = mutableListOf<String>()
        languages.forEach { lang ->
            paths.add("/$lang")
            paths.add("/$lang/events")
            if (includeDetail) {
                paths.add("/$lang/events/${event.id}")
            }
        }
        return paths.distinct()
    }

    private fun resolveLanguages(event: Event): Set<String> {
        return try {
            mergeLanguages(venueApi.getVenueLanguages(event.venueId))
        } catch (ex: Exception) {
            // Fallback: use event translations + en if venue lookup fails.
            mergeLanguages(event.translations.map { it.language })
        }
    }

    private fun mergeLanguages(languages: Collection<String>): Set<String> {
        val langs = languages.map { it.lowercase(Locale.getDefault()) }.toMutableSet()
        langs.addAll(defaultLanguages)
        return langs
    }

    private fun runAfterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    action()
                }
            })
        } else {
            action()
        }
    }
}
