package app.venues.platform.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.platform.api.dto.PlatformSubscriptionResponse
import app.venues.platform.domain.WebhookSubscription
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookSubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class WebhookSubscriptionService(
    private val subscriptionRepository: WebhookSubscriptionRepository,
    private val platformRepository: PlatformRepository,
    private val eventApi: EventApi
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Subscribe a platform to an event's webhooks.
     */
    fun subscribe(platformId: UUID, eventId: UUID): PlatformSubscriptionResponse {
        if (!platformRepository.existsById(platformId)) {
            throw VenuesException.ResourceNotFound("Platform not found: $platformId")
        }

        // Verify event exists (optional, but good practice)
        // We use eventApi to check, but since we don't have a direct "exists" method in API,
        // we might skip strict validation or fetch simple info.
        // Assuming eventApi.getEventInfo(eventId) exists or similar.
        // If not, we trust the admin or add a method to EventApi.
        // For now, we'll proceed assuming valid UUIDs, or check if event exists via API if available.

        if (subscriptionRepository.existsByPlatformIdAndEventId(platformId, eventId)) {
            logger.debug { "Platform $platformId already subscribed to event $eventId" }
            return PlatformSubscriptionResponse(platformId, eventId, true)
        }

        val subscription = WebhookSubscription(platformId = platformId, eventId = eventId)
        subscriptionRepository.save(subscription)

        logger.info { "Subscribed platform $platformId to event $eventId" }
        return PlatformSubscriptionResponse(platformId, eventId, true)
    }

    /**
     * Unsubscribe a platform from an event's webhooks.
     */
    fun unsubscribe(platformId: UUID, eventId: UUID) {
        if (subscriptionRepository.existsByPlatformIdAndEventId(platformId, eventId)) {
            subscriptionRepository.deleteByPlatformIdAndEventId(platformId, eventId)
            logger.info { "Unsubscribed platform $platformId from event $eventId" }
        }
    }

    /**
     * Get all subscriptions for a platform.
     */
    fun getSubscriptions(platformId: UUID): List<PlatformSubscriptionResponse> {
        return subscriptionRepository.findByPlatformId(platformId).map {
            PlatformSubscriptionResponse(it.platformId, it.eventId, true)
        }
    }

    /**
     * Bulk subscribe a platform to multiple events.
     */
    fun bulkSubscribe(platformId: UUID, eventIds: List<UUID>): Int {
        if (!platformRepository.existsById(platformId)) {
            throw VenuesException.ResourceNotFound("Platform not found: $platformId")
        }

        var count = 0
        eventIds.forEach { eventId ->
            if (!subscriptionRepository.existsByPlatformIdAndEventId(platformId, eventId)) {
                subscriptionRepository.save(WebhookSubscription(platformId = platformId, eventId = eventId))
                count++
            }
        }
        logger.info { "Bulk subscribed platform $platformId to $count new events" }
        return count
    }
}

