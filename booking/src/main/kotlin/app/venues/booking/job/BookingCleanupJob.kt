package app.venues.booking.job

import app.venues.booking.service.BookingCleanupService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled job for cleaning up expired bookings.
 */
@Component
class BookingCleanupJob(
    private val bookingCleanupService: BookingCleanupService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Expire pending bookings.
     * Runs every minute.
     */
    @Scheduled(fixedRate = 60000)
    fun cleanupExpiredBookings() {
        try {
            bookingCleanupService.expirePendingBookings()
        } catch (e: Exception) {
            logger.error(e) { "Error during booking cleanup job" }
        }
    }
}
