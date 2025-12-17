package app.venues.platform.repository

import app.venues.platform.domain.WebhookSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface WebhookSubscriptionRepository : JpaRepository<WebhookSubscription, UUID> {

    fun findByEventId(eventId: UUID): List<WebhookSubscription>

    fun findByPlatformId(platformId: UUID): List<WebhookSubscription>

    fun existsByPlatformIdAndEventId(platformId: UUID, eventId: UUID): Boolean

    fun deleteByPlatformIdAndEventId(platformId: UUID, eventId: UUID)
}

