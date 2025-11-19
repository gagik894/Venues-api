package app.venues.booking.job

import app.venues.booking.service.CartCleanupService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component


/**
 * Scheduled job for cleaning up expired cart items.
 *
 * Runs every minute to automatically delete expired cart items.
 */
@Component
class CartCleanupJob(
    private val cartCleanupService: CartCleanupService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Delete expired cart items.
     *
     * Runs: Every minute
     */
    @Scheduled(fixedRate = 60000) // 60 seconds
    fun cleanupExpiredCarts() {
        try {
            val deletedCount = cartCleanupService.deleteExpiredCarts()

            if (deletedCount > 0) {
                logger.info { "Cart cleanup completed: Deleted $deletedCount expired items" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during cart cleanup job" }
        }
    }
}
