package app.venues.booking.job

import app.venues.booking.service.CartCleanupService
import app.venues.shared.scheduling.AdaptiveScheduledTask
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct


/**
 * Scheduled job for cleaning up expired cart items.
 *
 * Uses adaptive scheduling: if no work found for 5 consecutive runs (5 minutes),
 * skips execution to allow Neon DB to scale to 0. After max skips, checks every 6 minutes.
 */
@Component
class CartCleanupJob(
    private val cartCleanupService: CartCleanupService
) {
    private val logger = KotlinLogging.logger {}
    private lateinit var adaptiveTask: AdaptiveScheduledTask

    @PostConstruct
    fun init() {
        adaptiveTask = AdaptiveScheduledTask(
            taskName = "CartCleanup",
            maxConsecutiveSkips = 5, // Skip 5 runs = 5 minutes of inactivity
            checkIntervalAfterMaxSkips = 360000 // Check every 6 minutes after max skips
        )
    }

    /**
     * Delete expired cart items.
     *
     * Runs: Every minute, but skips execution if no work found for 5+ consecutive runs.
     * This allows Neon DB to scale to 0 during idle periods.
     */
    @Scheduled(fixedRate = 60000) // 60 seconds
    fun cleanupExpiredCarts() {
        val result = adaptiveTask.executeIfNeeded {
            cartCleanupService.deleteExpiredCarts()
        }

        if (result.executed && result.workFound) {
            logger.info { "Cart cleanup completed: Deleted ${result.workCount} expired items" }
        } else if (!result.executed) {
            // Skipped - no DB connection made, allowing Neon to scale to 0
            logger.trace { "Cart cleanup skipped (no work found in last ${result.skipCount} runs)" }
        }
    }
}
