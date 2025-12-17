package app.venues.platform.service

import app.venues.platform.api.AvailablePlatformDto
import app.venues.platform.api.PlatformSubscriptionApi
import app.venues.platform.domain.PlatformStatus
import app.venues.platform.domain.WebhookSubscription
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookSubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class PlatformSubscriptionApiService(
    private val subscriptionRepository: WebhookSubscriptionRepository,
    private val platformRepository: PlatformRepository
) : PlatformSubscriptionApi {

    private val logger = KotlinLogging.logger {}

    override fun updateEventSubscriptions(eventId: UUID, platformIds: List<UUID>) {
        val currentSubs = subscriptionRepository.findByEventId(eventId)
        val currentPlatformIds = currentSubs.map { it.platformId }.toSet()
        val newPlatformIds = platformIds.toSet()

        // Add new subscriptions
        val toAdd = newPlatformIds - currentPlatformIds
        toAdd.forEach { platformId ->
            if (platformRepository.existsById(platformId)) {
                subscriptionRepository.save(
                    WebhookSubscription(platformId = platformId, eventId = eventId)
                )
            } else {
                logger.warn { "Platform $platformId not found - skipping subscription" }
            }
        }

        // Remove obsolete subscriptions
        val toRemove = currentPlatformIds - newPlatformIds
        toRemove.forEach { platformId ->
            subscriptionRepository.deleteByPlatformIdAndEventId(platformId, eventId)
        }

        logger.info {
            "Updated subscriptions for event $eventId: added ${toAdd.size}, removed ${toRemove.size}"
        }
    }

    override fun getEventSubscriptions(eventId: UUID): List<UUID> {
        return subscriptionRepository.findByEventId(eventId).map { it.platformId }
    }

    override fun getAvailablePlatforms(): List<AvailablePlatformDto> {
        return platformRepository.findByStatus(PlatformStatus.ACTIVE, Pageable.unpaged())
            .content
            .map { platform ->
                AvailablePlatformDto(
                    id = platform.id,
                    name = platform.name,
                    status = platform.status.name
                )
            }
    }
}

