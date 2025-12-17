package app.venues.booking.service

import app.venues.booking.api.domain.BookingStatus
import app.venues.booking.repository.BookingRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Service for cleaning up expired bookings.
 */
@Service
class BookingCleanupService(
    private val bookingRepository: BookingRepository,
    private val bookingService: BookingService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Expire pending bookings older than 15 minutes.
     */
    fun expirePendingBookings(): Int {
        val cutoff = Instant.now().minus(15, ChronoUnit.MINUTES)
        val expiredBookings = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff)

        if (expiredBookings.isEmpty()) {
            return 0
        }

        var expiredCount = 0
        expiredBookings.forEach { booking ->
            try {
                bookingService.expireBooking(booking.id)
                expiredCount++
            } catch (e: Exception) {
                logger.error(e) { "Failed to expire booking ${booking.id}" }
            }
        }

        if (expiredCount > 0) {
            logger.info { "Expired $expiredCount pending bookings" }
        }

        return expiredCount
    }
}
