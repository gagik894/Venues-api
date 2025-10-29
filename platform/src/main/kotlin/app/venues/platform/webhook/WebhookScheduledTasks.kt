package app.venues.platform.webhook

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled tasks for webhook management.
 *
 * - Retry failed webhooks with exponential backoff
 * - Clean up old webhook events
 */
@Component
class WebhookScheduledTasks(
    private val webhookService: WebhookService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Retry failed webhooks every 1 minute
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    fun retryFailedWebhooks() {
        try {
            logger.debug { "Running scheduled webhook retry task" }
            webhookService.retryFailedWebhooks()
        } catch (e: Exception) {
            logger.error(e) { "Error in webhook retry task" }
        }
    }
}

