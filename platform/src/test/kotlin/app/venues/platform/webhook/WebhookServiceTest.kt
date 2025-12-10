package app.venues.platform.webhook

import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSessionDto
import app.venues.platform.api.dto.SeatClosedPayload
import app.venues.platform.api.dto.TableClosedPayload
import app.venues.platform.domain.Platform
import app.venues.platform.domain.WebhookEvent
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookEventRepository
import app.venues.platform.repository.WebhookSubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

class WebhookServiceTest {

    private lateinit var webhookService: WebhookService
    private val platformRepository: PlatformRepository = mockk()
    private val webhookEventRepository: WebhookEventRepository = mockk()
    private val webhookSubscriptionRepository: WebhookSubscriptionRepository = mockk()
    private val eventApi: EventApi = mockk()
    private val webClient: WebClient = mockk()
    private val objectMapper = ObjectMapper()
    private lateinit var requestSpec: WebClient.RequestBodyUriSpec
    private lateinit var requestHeadersSpec: WebClient.RequestBodySpec
    private lateinit var responseSpec: WebClient.ResponseSpec

    private val platformId = UUID.randomUUID()
    private val platform = Platform(
        name = "p1",
        apiUrl = "https://platform.test",
        sharedSecret = "secret"
    ).apply {
        val idField = this.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, platformId)
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()

        responseSpec = mockk()
        requestHeadersSpec = mockk()
        requestSpec = mockk()

        every { webClient.post() } returns requestSpec
        every { requestSpec.uri(any<String>()) } returns requestHeadersSpec
        every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
        every { requestHeadersSpec.bodyValue(any<String>()) } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
        every { responseSpec.toBodilessEntity() } returns Mono.just(mockk(relaxed = true))

        webhookService = WebhookService(
            platformRepository = platformRepository,
            webhookEventRepository = webhookEventRepository,
            webhookSubscriptionRepository = webhookSubscriptionRepository,
            eventApi = eventApi,
            webClient = webClient,
            objectMapper = objectMapper
        )
    }

    @Test
    fun `seat reserved webhook includes body hash and signature covers body`() {
        val payload = SeatClosedPayload(
            timestamp = Instant.now().toString(),
            sessionId = UUID.randomUUID(),
            seatIdentifier = "A1"
        )

        val sessionId = payload.sessionId
        val eventId = UUID.randomUUID()

        every { eventApi.getEventSessionInfo(sessionId) } returns EventSessionDto(
            sessionId = sessionId,
            eventId = eventId,
            venueId = UUID.randomUUID(),
            eventTitle = "t",
            eventDescription = null,
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )
        every { webhookSubscriptionRepository.findByEventId(eventId) } returns listOf(
            app.venues.platform.domain.WebhookSubscription(platformId = platformId, eventId = eventId)
        )
        every {
            platformRepository.findByIdInAndStatusAndWebhookEnabled(
                ids = listOf(platformId),
                status = any(),
                webhookEnabled = true
            )
        } returns listOf(platform)

        every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
        every { requestHeadersSpec.bodyValue(any<String>()) } returns requestHeadersSpec
        every { webhookEventRepository.save(any<WebhookEvent>()) } answers { firstArg() }

        webhookService.notifySeatClosed(
            sessionId = payload.sessionId,
            seatIdentifier = payload.seatIdentifier
        )

        verify { webhookEventRepository.save(any<WebhookEvent>()) }
    }

    @Test
    fun `webhook failure marks event failed`() {
        val payload = TableClosedPayload(
            timestamp = Instant.now().toString(),
            sessionId = UUID.randomUUID(),
            tableIdentifier = "T1"
        )

        val sessionId = payload.sessionId
        val eventId = UUID.randomUUID()

        every { eventApi.getEventSessionInfo(sessionId) } returns EventSessionDto(
            sessionId = sessionId,
            eventId = eventId,
            venueId = UUID.randomUUID(),
            eventTitle = "t",
            eventDescription = null,
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )
        every { webhookSubscriptionRepository.findByEventId(eventId) } returns listOf(
            app.venues.platform.domain.WebhookSubscription(platformId = platformId, eventId = eventId)
        )
        every {
            platformRepository.findByIdInAndStatusAndWebhookEnabled(
                ids = listOf(platformId),
                status = any(),
                webhookEnabled = true
            )
        } returns listOf(platform)

        val savedEvents = mutableListOf<WebhookEvent>()
        every { webhookEventRepository.save(capture(savedEvents)) } answers { savedEvents.last() }

        val responseSpec: WebClient.ResponseSpec = mockk()
        val requestHeadersSpec: WebClient.RequestBodySpec = mockk()
        val requestSpec: WebClient.RequestBodyUriSpec = mockk()

        every { webClient.post() } returns requestSpec
        every { requestSpec.uri(any<String>()) } returns requestHeadersSpec
        every { requestHeadersSpec.header(any(), any()) } returns requestHeadersSpec
        every { requestHeadersSpec.bodyValue(any<String>()) } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
        every {
            responseSpec.toBodilessEntity()
        } throws WebClientResponseException.create(
            HttpStatus.BAD_REQUEST.value(),
            "bad",
            HttpHeaders.EMPTY,
            ByteArray(0),
            null
        )

        webhookService.notifyTableClosed(
            sessionId = payload.sessionId,
            tableIdentifier = payload.tableIdentifier
        )

        assertTrue(savedEvents.isNotEmpty())
    }
}

