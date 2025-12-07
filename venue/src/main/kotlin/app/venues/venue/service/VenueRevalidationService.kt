package app.venues.venue.service

import app.venues.shared.web.revalidation.RevalidationNotifier
import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenueStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.*

@Service
class VenueRevalidationService(
    private val revalidationNotifier: RevalidationNotifier
) {

    private val logger = KotlinLogging.logger {}
    private val defaultLanguages = setOf("en", "hy", "ru")

    fun revalidate(venue: Venue, reason: String, force: Boolean = false) {
        if (!force && venue.status != VenueStatus.ACTIVE) {
            return
        }

        val domain = venue.customDomain
        if (domain.isNullOrBlank()) {
            logger.debug { "Skipping venue revalidation: custom domain missing for venue=${venue.id}" }
            return
        }

        val paths = buildPaths(venue)
        if (paths.isEmpty()) return

        runAfterCommit {
            revalidationNotifier.revalidate(domain, paths, reason)
        }
    }

    private fun buildPaths(venue: Venue): List<String> {
        val languages = venue.translations.map { it.language.lowercase(Locale.getDefault()) }.toMutableSet()
        languages.addAll(defaultLanguages)
        val paths = mutableListOf<String>()
        languages.forEach { lang ->
            paths.add("/$lang")
            paths.add("/$lang/events")
            paths.add("/$lang/about")
            paths.add("/$lang/contact")
            paths.add("/$lang/privacy")
            paths.add("/$lang/terms")
        }
        return paths.distinct()
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
