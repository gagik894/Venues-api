package app.venues.platform.webhook

import app.venues.shared.scheduling.AdaptiveScheduledTask
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * Scheduled tasks for webhook management.
 *
 * - Retry failed webhooks with exponential backoff
 * - Clean up old webhook events
 *
 * Uses adaptive scheduling: if no work found for 5 consecutive runs (5 minutes),
 * skips execution to allow Neon DB to scale to 0. After max skips, checks every 6 minutes.
 */
@Component
class WebhookScheduledTasks(
    private val webhookService: WebhookService
) {
    private val logger = KotlinLogging.logger {}
    private lateinit var adaptiveTask: AdaptiveScheduledTask

    @PostConstruct
    fun init() {
        adaptiveTask = AdaptiveScheduledTask(
            taskName = "WebhookRetry",
            maxConsecutiveSkips = 5, // Skip 5 runs = 5 minutes of inactivity
            checkIntervalAfterMaxSkips = 360000 // Check every 6 minutes after max skips
        )
    }

    /**
     * Retry failed webhooks every 1 minute.
     * Skips execution if no work found for 5+ consecutive runs.
     * This allows Neon DB to scale to 0 during idle periods.
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    fun retryFailedWebhooks() {
        val result = adaptiveTask.executeIfNeeded {
            webhookService.retryFailedWebhooks()
        }

        if (result.executed && result.workFound) {
            logger.debug { "Webhook retry completed: Processed ${result.workCount} webhooks" }
        } else if (!result.executed) {
            // Skipped - no DB connection made, allowing Neon to scale to 0
            logger.trace { "Webhook retry skipped (no work found in last ${result.skipCount} runs)" }
        }
    }
}

