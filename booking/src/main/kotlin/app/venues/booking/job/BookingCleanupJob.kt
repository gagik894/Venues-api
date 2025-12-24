package app.venues.booking.job

import app.venues.booking.service.BookingCleanupService
import app.venues.shared.scheduling.AdaptiveScheduledTask
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * Scheduled job for cleaning up expired bookings.
 *
 * Uses adaptive scheduling: if no work found for 5 consecutive runs (5 minutes),
 * skips execution to allow Neon DB to scale to 0. After max skips, checks every 6 minutes.
 */
@Component
class BookingCleanupJob(
    private val bookingCleanupService: BookingCleanupService
) {
    private val logger = KotlinLogging.logger {}
    private lateinit var adaptiveTask: AdaptiveScheduledTask

    @PostConstruct
    fun init() {
        adaptiveTask = AdaptiveScheduledTask(
            taskName = "BookingCleanup",
            maxConsecutiveSkips = 5, // Skip 5 runs = 5 minutes of inactivity
            checkIntervalAfterMaxSkips = 360000 // Check every 6 minutes after max skips
        )
    }

    /**
     * Expire pending bookings.
     * Runs every minute, but skips execution if no work found for 5+ consecutive runs.
     * This allows Neon DB to scale to 0 during idle periods.
     */
    @Scheduled(fixedRate = 60000)
    fun cleanupExpiredBookings() {
        val result = adaptiveTask.executeIfNeeded {
            bookingCleanupService.expirePendingBookings()
        }

        if (result.executed && result.workFound) {
            logger.info { "Booking cleanup completed: Expired ${result.workCount} bookings" }
        } else if (!result.executed) {
            // Skipped - no DB connection made, allowing Neon to scale to 0
            logger.trace { "Booking cleanup skipped (no work found in last ${result.skipCount} runs)" }
        }
    }
}
