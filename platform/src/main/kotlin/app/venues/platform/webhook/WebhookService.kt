package app.venues.platform.webhook

import app.venues.platform.api.dto.GAAvailabilityChangedPayload
import app.venues.platform.api.dto.SeatReleasedPayload
import app.venues.platform.api.dto.SeatReservedPayload
import app.venues.platform.api.dto.WebhookPayload
import app.venues.platform.domain.Platform
import app.venues.platform.domain.WebhookEvent
import app.venues.platform.domain.WebhookEventType
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookEventRepository
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
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Service for sending webhook callbacks to platforms.
 *
 * Handles:
 * - Sending webhook notifications to all active platforms
 * - HMAC signature generation for security
 * - Retry logic with exponential backoff
 * - Webhook event logging and tracking
 */
@Service
@Transactional
class WebhookService(
    private val platformRepository: PlatformRepository,
    private val webhookEventRepository: WebhookEventRepository,
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
     * Notify all platforms about seat reservation
     */
    @Async
    fun notifySeatReserved(
        sessionId: Long,
        seatIdentifier: String,
        reservationToken: String,
        expiresAt: String
    ) {
        logger.debug { "Notifying platforms: seat reserved - $seatIdentifier" }

        val payload = SeatReservedPayload(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
            reservationToken = java.util.UUID.fromString(reservationToken),
            expiresAt = expiresAt
        )

        sendToAllPlatforms(
            eventType = WebhookEventType.SEAT_RESERVED,
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
            payload = payload
        )
    }

    /**
     * Notify all platforms about seat release
     */
    @Async
    fun notifySeatReleased(
        sessionId: Long,
        seatIdentifier: String,
        levelName: String
    ) {
        logger.debug { "Notifying platforms: seat released - $seatIdentifier" }

        val payload = SeatReleasedPayload(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
            levelName = levelName
        )

        sendToAllPlatforms(
            eventType = WebhookEventType.SEAT_RELEASED,
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
            payload = payload
        )
    }

    /**
     * Notify all platforms about GA availability change
     */
    @Async
    fun notifyGAAvailabilityChanged(
        sessionId: Long,
        levelIdentifier: String,
        levelName: String,
        availableTickets: Int,
        totalCapacity: Int
    ) {
        logger.debug { "Notifying platforms: GA availability changed - $levelIdentifier" }

        val payload = GAAvailabilityChangedPayload(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            levelIdentifier = levelIdentifier,
            levelName = levelName,
            availableTickets = availableTickets,
            totalCapacity = totalCapacity
        )

        sendToAllPlatforms(
            eventType = WebhookEventType.GA_AVAILABILITY_CHANGED,
            sessionId = sessionId,
            levelIdentifier = levelIdentifier,
            payload = payload
        )
    }

    // ===========================================
    // INTERNAL - Webhook Delivery
    // ===========================================

    /**
     * Send webhook to all active platforms
     */
    private fun sendToAllPlatforms(
        eventType: WebhookEventType,
        sessionId: Long,
        seatIdentifier: String? = null,
        levelIdentifier: String? = null,
        payload: WebhookPayload
    ) {
        val platforms = platformRepository.findByStatusAndWebhookEnabled(
            status = app.venues.platform.domain.PlatformStatus.ACTIVE,
            webhookEnabled = true
        )

        logger.info { "Sending webhook to ${platforms.size} platforms: $eventType" }

        platforms.forEach { platform ->
            try {
                createAndSendWebhook(
                    platform = platform,
                    eventType = eventType,
                    sessionId = sessionId,
                    seatIdentifier = seatIdentifier,
                    levelIdentifier = levelIdentifier,
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
        sessionId: Long,
        seatIdentifier: String?,
        levelIdentifier: String?,
        payload: WebhookPayload
    ) {
        // Serialize payload
        val payloadJson = objectMapper.writeValueAsString(payload)

        // Create webhook event record
        val webhookEvent = WebhookEvent(
            platformId = platform.id!!,
            eventType = eventType,
            sessionId = sessionId,
            seatIdentifier = seatIdentifier,
            levelIdentifier = levelIdentifier,
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
        try {
            val url = "${platform.apiUrl}$WEBHOOK_ENDPOINT"
            val timestamp = Instant.now().toString()
            val nonce = java.util.UUID.randomUUID().toString()
            val signature = generateSignature(platform.id ?: 0L, timestamp, nonce, platform.sharedSecret)

            logger.debug { "Sending webhook to ${platform.name} at $url" }

            val response = webClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(SIGNATURE_HEADER, signature)
                .header(TIMESTAMP_HEADER, timestamp)
                .header(NONCE_HEADER, nonce)
                .header(EVENT_TYPE_HEADER, webhookEvent.eventType.name)
                .bodyValue(payloadJson)
                .retrieve()
                .toBodilessEntity()
                .block()

            val statusCode = response?.statusCode?.value() ?: 200
            webhookEvent.markAsDelivered(statusCode, "Success")
            webhookEventRepository.save(webhookEvent)

            logger.info { "Webhook delivered successfully to ${platform.name}: ${webhookEvent.id}" }

        } catch (e: WebClientResponseException) {
            val errorMessage = "HTTP ${e.statusCode.value()}: ${e.responseBodyAsString}"
            webhookEvent.markAsFailed(e.statusCode.value(), errorMessage)
            webhookEventRepository.save(webhookEvent)

            logger.warn { "Webhook delivery failed to ${platform.name}: $errorMessage" }

        } catch (e: Exception) {
            val errorMessage = "Delivery error: ${e.message}"
            webhookEvent.markAsFailed(null, errorMessage)
            webhookEventRepository.save(webhookEvent)

            logger.error(e) { "Webhook delivery failed to ${platform.name}" }
        }
    }

    /**
     * Generate HMAC-SHA256 signature for webhook
     * Signature format: HMAC-SHA256(platformId|timestamp|nonce, sharedSecret)
     */
    private fun generateSignature(platformId: Long, timestamp: String, nonce: String, secret: String): String {
        val data = "$platformId|$timestamp|$nonce"
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val hmac = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return hmac.joinToString("") { "%02x".format(it) }
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
        val pendingWebhooks = webhookEventRepository.findPendingRetries(
            now = now,
            maxAttempts = WebhookEvent.MAX_RETRY_ATTEMPTS,
            pageable = org.springframework.data.domain.PageRequest.of(0, 100)
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
}

