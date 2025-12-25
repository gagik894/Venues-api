package app.venues.platform.webhook

import app.venues.event.api.EventApi
import app.venues.platform.api.dto.*
import app.venues.platform.domain.Platform
import app.venues.platform.domain.PlatformStatus
import app.venues.platform.domain.WebhookEvent
import app.venues.platform.domain.WebhookEventType
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookEventRepository
import app.venues.platform.repository.WebhookSubscriptionRepository
import app.venues.shared.persistence.util.PageableMapper
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Service for sending webhook callbacks to platforms.
 *
 * Handles:
 * - Sending webhook notifications to subscribed active platforms
 * - HMAC signature generation for security
 * - Retry logic with exponential backoff
 * - Webhook event logging and tracking
 */
@Service
@Transactional
class WebhookService(
    private val platformRepository: PlatformRepository,
    private val webhookEventRepository: WebhookEventRepository,
    private val webhookSubscriptionRepository: WebhookSubscriptionRepository,
    private val eventApi: EventApi,
    @Qualifier("webhookWebClient") private val webClient: WebClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val WEBHOOK_ENDPOINT = "/webhooks/venues-api"
        private const val SIGNATURE_HEADER = "X-Venues-Signature"
        private const val TIMESTAMP_HEADER = "X-Venues-Timestamp"
        private const val NONCE_HEADER = "X-Venues-Nonce"
        private const val EVENT_TYPE_HEADER = "X-Venues-Event-Type"
    }

    // ===========================================
    // PUBLIC API - Send Webhooks
    // ===========================================

    /**
     * Notify subscribed platforms about seat reservation
     */
    @Async
    fun notifySeatClosed(
        sessionId: UUID,
        seatIdentifier: String
    ) {
        logger.debug { "Notifying platforms: seat closed - $seatIdentifier" }

        val payload = SeatClosedPayload(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
        )

        sendToSubscribers(
            eventType = WebhookEventType.SEAT_CLOSED,
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
            payload = payload
        )
    }

    /**
     * Notify subscribed platforms about seat release
     */
    @Async
    fun notifySeatOpened(
        sessionId: UUID,
        seatIdentifier: String,
    ) {
        logger.debug { "Notifying platforms: seat opened - $seatIdentifier" }

        val payload = SeatOpenedPayload(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
        )

        sendToSubscribers(
            eventType = WebhookEventType.SEAT_OPENED,
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
            payload = payload
        )
    }

    /**
     * Notify subscribed platforms about GA availability change
     */
    @Async
    fun notifyGAAvailabilityChanged(
        sessionId: UUID,
        levelIdentifier: String,
        availableTickets: Int
    ) {
        logger.debug { "Notifying platforms: GA availability changed - $levelIdentifier" }

        val payload = GAAvailabilityChangedPayload(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            levelIdentifier = levelIdentifier,
            availableTickets = availableTickets
        )

        sendToSubscribers(
            eventType = WebhookEventType.GA_AVAILABILITY_CHANGED,
            sessionId = sessionId,
            levelIdentifier = levelIdentifier,
            payload = payload
        )
    }

    /**
     * Notify subscribed platforms about table reservation
     */
    @Async
    fun notifyTableClosed(
        sessionId: UUID,
        tableIdentifier: String
    ) {
        logger.debug { "Notifying platforms: table closed - $tableIdentifier" }

        val payload = TableClosedPayload(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            tableIdentifier = tableIdentifier
        )

        sendToSubscribers(
            eventType = WebhookEventType.TABLE_CLOSED,
            sessionId = sessionId,
            tableIdentifier = tableIdentifier,
            payload = payload
        )
    }

    /**
     * Notify subscribed platforms about table release
     */
    @Async
    fun notifyTableOpened(
        sessionId: UUID,
        tableIdentifier: String
    ) {
        logger.debug { "Notifying platforms: table opened - $tableIdentifier" }

        val payload = TableOpenedPayload(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            tableIdentifier = tableIdentifier
        )

        sendToSubscribers(
            eventType = WebhookEventType.TABLE_OPENED,
            sessionId = sessionId,
            tableIdentifier = tableIdentifier,
            payload = payload
        )
    }

    // ===========================================
    // INTERNAL - Webhook Delivery
    // ===========================================

    /**
     * Send webhook to subscribed active platforms
     */
    private fun sendToSubscribers(
        eventType: WebhookEventType,
        sessionId: UUID,
        seatIdentifier: String? = null,
        levelIdentifier: String? = null,
        tableIdentifier: String? = null,
        payload: WebhookPayload
    ) {
        val sessionInfo = eventApi.getEventSessionInfo(sessionId)
        if (sessionInfo == null) {
            logger.warn { "Could not find session info for session $sessionId. Skipping webhook broadcast." }
            return
        }

        val subscriptions = webhookSubscriptionRepository.findByEventId(sessionInfo.eventId)
        if (subscriptions.isEmpty()) {
            logger.debug { "No subscriptions found for event ${sessionInfo.eventId} (session $sessionId)" }
            return
        }

        val platformIds = subscriptions.map { it.platformId }
        val platforms = platformRepository.findByIdInAndStatusAndWebhookEnabled(
            ids = platformIds,
            status = PlatformStatus.ACTIVE,
            webhookEnabled = true
        )

        logger.info { "Sending webhook to ${platforms.size} subscribers for event ${sessionInfo.eventId}: $eventType" }

        platforms.forEach { platform ->
            try {
                createAndSendWebhook(
                    platform = platform,
                    eventType = eventType,
                    sessionId = sessionId,
                    seatIdentifier = seatIdentifier,
                    levelIdentifier = levelIdentifier,
                    tableIdentifier = tableIdentifier,
                    payload = payload
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to send webhook to platform ${platform.name}" }
            }
        }
    }

    /**
     * Create webhook event and attempt delivery
     */
    private fun createAndSendWebhook(
        platform: Platform,
        eventType: WebhookEventType,
        sessionId: UUID,
        seatIdentifier: String?,
        levelIdentifier: String?,
        tableIdentifier: String?,
        payload: WebhookPayload
    ) {
        // Serialize payload
        val payloadJson = objectMapper.writeValueAsString(payload)

        // Create webhook event record
        val webhookEvent = WebhookEvent(
            platformId = platform.id,
            eventType = eventType,
            sessionId = sessionId,
            seatCode = seatIdentifier,
            gaAreaCode = levelIdentifier,
            tableCode = tableIdentifier,
            payload = payloadJson
        )

        webhookEventRepository.save(webhookEvent)

        // Attempt delivery asynchronously
        deliverWebhook(webhookEvent, platform, payloadJson)
    }

    /**
     * Deliver webhook to platform
     */
    private fun deliverWebhook(
        webhookEvent: WebhookEvent,
        platform: Platform,
        payloadJson: String
    ) {
        val url = "${platform.apiUrl}$WEBHOOK_ENDPOINT"
        val timestamp = Instant.now().toString()
        val nonce = UUID.randomUUID().toString()
        val bodyHash = sha256(payloadJson)
        val signature = generateSignature(platform.id, timestamp, nonce, bodyHash, platform.sharedSecret)

        logger.debug { "Sending webhook to ${platform.name} at $url" }

        webClient.post()
            .uri(url)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SIGNATURE_HEADER, signature)
            .header(TIMESTAMP_HEADER, timestamp)
            .header(NONCE_HEADER, nonce)
            .header(EVENT_TYPE_HEADER, webhookEvent.eventType.name)
            .header("X-Venues-Body-Hash", bodyHash)
            .bodyValue(payloadJson)
            .retrieve()
            .toBodilessEntity()
            .subscribe(
                { response ->
                    val statusCode = response.statusCode.value()
                    webhookEvent.markAsDelivered(statusCode, "Success")
                    webhookEventRepository.save(webhookEvent)
                    logger.info { "Webhook delivered successfully to ${platform.name}: ${webhookEvent.id}" }
                },
                { error ->
                    when (error) {
                        is WebClientResponseException -> {
                            val errorMessage = "HTTP ${error.statusCode.value()}: ${error.responseBodyAsString}"
                            webhookEvent.markAsFailed(error.statusCode.value(), errorMessage)
                            logger.warn { "Webhook delivery failed to ${platform.name}: $errorMessage" }
                        }

                        else -> {
                            val errorMessage = "Delivery error: ${error.message}"
                            webhookEvent.markAsFailed(null, errorMessage)
                            logger.error(error) { "Webhook delivery failed to ${platform.name}" }
                        }
                    }
                    webhookEventRepository.save(webhookEvent)
                }
            )
    }

    /**
     * Generate HMAC-SHA256 signature for webhook
     * Signature format: HMAC-SHA256(platformId|timestamp|nonce, sharedSecret)
     */
    private fun generateSignature(
        platformId: UUID,
        timestamp: String,
        nonce: String,
        bodyHash: String,
        secret: String
    ): String {
        val data = "webhook|$platformId|$timestamp|$nonce|$bodyHash"
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val hmac = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return hmac.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(payload.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ===========================================
    // RETRY LOGIC
    // ===========================================

    /**
     * Retry failed webhooks (called by scheduled job)
     */
    fun retryFailedWebhooks() {
        logger.debug { "Checking for webhooks to retry..." }

        val now = Instant.now()
        val pageable = PageableMapper.createPageableUnsorted(limit = 100, offset = 0)
        val pendingWebhooks = webhookEventRepository.findPendingRetries(
            now = now,
            maxAttempts = WebhookEvent.MAX_RETRY_ATTEMPTS,
            pageable = pageable
        )

        logger.info { "Found ${pendingWebhooks.totalElements} webhooks to retry" }

        pendingWebhooks.forEach { webhookEvent ->
            if (webhookEvent.shouldRetry()) {
                try {
                    // Fetch platform by ID
                    val platform = platformRepository.findById(webhookEvent.platformId)
                        .orElseThrow { IllegalStateException("Platform not found: ${webhookEvent.platformId}") }

                    deliverWebhook(webhookEvent, platform, webhookEvent.payload)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to retry webhook ${webhookEvent.id}" }
                }
            }
        }
    }

    /**
     * Replay a specific webhook event on demand (admin/API use only).
     */
    fun replayWebhook(eventId: UUID) {
        val event = webhookEventRepository.findById(eventId)
            .orElseThrow { IllegalArgumentException("Webhook event not found: $eventId") }

        if (event.attemptCount >= WebhookEvent.MAX_RETRY_ATTEMPTS) {
            throw IllegalStateException("Max retry attempts reached for webhook $eventId")
        }

        val platform = platformRepository.findById(event.platformId)
            .orElseThrow { IllegalStateException("Platform not found: ${event.platformId}") }

        event.resetForReplay()
        webhookEventRepository.save(event)

        deliverWebhook(event, platform, event.payload)
    }
}
