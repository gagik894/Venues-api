package app.venues.platform.service

import app.venues.platform.domain.Platform
import app.venues.platform.domain.PlatformStatus
import app.venues.platform.domain.WebhookSubscription
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookSubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.*

class PlatformSubscriptionApiServiceTest {

    private lateinit var subscriptionRepository: WebhookSubscriptionRepository
    private lateinit var platformRepository: PlatformRepository
    private lateinit var service: PlatformSubscriptionApiService

    private val eventId = UUID.randomUUID()
    private val platformA = UUID.randomUUID()
    private val platformB = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        subscriptionRepository = mockk(relaxed = true)
        platformRepository = mockk(relaxed = true)
        every { subscriptionRepository.save(any<WebhookSubscription>()) } answers { firstArg() }
        service = PlatformSubscriptionApiService(subscriptionRepository, platformRepository)
    }

    @Test
    fun `updateEventSubscriptions adds new platforms and removes obsolete ones`() {
        every { subscriptionRepository.findByEventId(eventId) } returns listOf(
            WebhookSubscription(platformId = platformA, eventId = eventId)
        )
        every { platformRepository.existsById(platformB) } returns true

        service.updateEventSubscriptions(eventId, listOf(platformA, platformB))

        // platformB should be added
        verify { subscriptionRepository.save(any<WebhookSubscription>()) }
        // platformA remains, so no delete
        verify(exactly = 0) { subscriptionRepository.deleteByPlatformIdAndEventId(platformA, eventId) }
    }

    @Test
    fun `updateEventSubscriptions removes platforms not in the new list`() {
        every { subscriptionRepository.findByEventId(eventId) } returns listOf(
            WebhookSubscription(platformId = platformA, eventId = eventId),
            WebhookSubscription(platformId = platformB, eventId = eventId)
        )
        every { platformRepository.existsById(any()) } returns true

        service.updateEventSubscriptions(eventId, listOf(platformB)) // remove platformA

        verify { subscriptionRepository.deleteByPlatformIdAndEventId(platformA, eventId) }
        verify(exactly = 0) { subscriptionRepository.deleteByPlatformIdAndEventId(platformB, eventId) }
    }

    @Test
    fun `getAvailablePlatforms returns active platforms`() {
        val platform = Platform(name = "P1", apiUrl = "https://example.com", sharedSecret = "secret")
        every { platformRepository.findByStatus(PlatformStatus.ACTIVE, Pageable.unpaged()) } returns
                PageImpl(listOf(platform))

        val result = service.getAvailablePlatforms()

        assert(result.size == 1)
        assert(result.first().name == "P1")
    }
}

