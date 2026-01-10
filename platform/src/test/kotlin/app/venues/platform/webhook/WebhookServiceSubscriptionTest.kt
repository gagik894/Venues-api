package app.venues.platform.webhook

import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSessionDto
import app.venues.platform.domain.Platform
import app.venues.platform.domain.PlatformStatus
import app.venues.platform.domain.WebhookSubscription
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookEventRepository
import app.venues.platform.repository.WebhookSubscriptionRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

class WebhookServiceSubscriptionTest {

    private val platformRepository: PlatformRepository = mockk(relaxed = true)
    private val webhookEventRepository: WebhookEventRepository = mockk(relaxed = true)
    private val subscriptionRepository: WebhookSubscriptionRepository = mockk(relaxed = true)
    private val eventApi: EventApi = mockk(relaxed = true)
    private val webClient: WebClient = mockk()
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `no subscriptions means no webhook events created`() {
        val sessionId = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        every { eventApi.getEventSessionInfo(sessionId) } returns EventSessionDto(
            sessionId = sessionId,
            eventId = eventId,
            venueId = UUID.randomUUID(),
            seatingChartId = UUID.randomUUID(),
            eventTitle = "Title",
            eventDescription = null,
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )
        every { subscriptionRepository.findByEventId(eventId) } returns emptyList()

        val service = WebhookService(
            platformRepository = platformRepository,
            webhookEventRepository = webhookEventRepository,
            webhookSubscriptionRepository = subscriptionRepository,
            eventApi = eventApi,
            webClient = webClient,
            objectMapper = objectMapper
        )

        service.notifySeatClosed(sessionId, "A1")

        verify(exactly = 1) { eventApi.getEventSessionInfo(sessionId) }
        verify(exactly = 1) { subscriptionRepository.findByEventId(eventId) }
        // No webhook event persisted because there are no subscribers
        verify(exactly = 0) { webhookEventRepository.save(any()) }
    }

    @Test
    fun `subscribed platforms are filtered by status and webhook enabled`() {
        val sessionId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val platformId = UUID.randomUUID()

        every { eventApi.getEventSessionInfo(sessionId) } returns EventSessionDto(
            sessionId = sessionId,
            eventId = eventId,
            venueId = UUID.randomUUID(),
            seatingChartId = UUID.randomUUID(),
            eventTitle = "Title",
            eventDescription = null,
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )

        every { subscriptionRepository.findByEventId(eventId) } returns listOf(
            WebhookSubscription(platformId = platformId, eventId = eventId)
        )

        val platform = Platform(
            name = "P1",
            apiUrl = "https://example.com",
            sharedSecret = "secret"
        ).apply {
            // defaults to ACTIVE + webhookEnabled
            enableWebhooks()
        }

        every {
            platformRepository.findByIdInAndStatusAndWebhookEnabled(
                ids = listOf(platformId),
                status = PlatformStatus.ACTIVE,
                webhookEnabled = true
            )
        } returns listOf(platform)

        // Mock WebClient chain to avoid real HTTP
        val requestSpec = mockk<WebClient.RequestBodyUriSpec>(relaxed = true)
        val responseSpec = mockk<WebClient.ResponseSpec>(relaxed = true)

        every { webClient.post() } returns requestSpec
        every { requestSpec.uri(any<String>()) } returns requestSpec
        every { requestSpec.header(any(), any()) } returns requestSpec
        every { requestSpec.bodyValue(any<String>()) } returns requestSpec
        every { requestSpec.retrieve() } returns responseSpec
        every { responseSpec.toBodilessEntity() } returns Mono.empty()

        val service = WebhookService(
            platformRepository = platformRepository,
            webhookEventRepository = webhookEventRepository,
            webhookSubscriptionRepository = subscriptionRepository,
            eventApi = eventApi,
            webClient = webClient,
            objectMapper = objectMapper
        )

        service.notifySeatClosed(sessionId, "A1")

        verify(exactly = 1) { webhookEventRepository.save(any()) }
        verify(exactly = 1) {
            platformRepository.findByIdInAndStatusAndWebhookEnabled(
                ids = listOf(platformId),
                status = PlatformStatus.ACTIVE,
                webhookEnabled = true
            )
        }
    }
}

