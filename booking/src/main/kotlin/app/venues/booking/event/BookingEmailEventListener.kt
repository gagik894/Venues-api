package app.venues.booking.event

import app.venues.booking.service.BookingConfirmationEmailService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Async event listener for booking-related email notifications.
 *
 * Listens to booking events and triggers email sending asynchronously
 * to avoid blocking the main request thread.
 *
 * Benefits:
 * - Non-blocking: API responses are not delayed by email sending
 * - Fault-tolerant: Email failures don't affect booking confirmation
 * - Scalable: Email sending can be scaled independently
 */
@Component
class BookingEmailEventListener(
    private val emailService: BookingConfirmationEmailService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle booking confirmed event - send confirmation email with tickets.
     *
     * Runs asynchronously to avoid blocking the booking confirmation response.
     */
    @Async
    @EventListener
    fun handleBookingConfirmed(event: BookingConfirmedEvent) {
        logger.debug { "Received BookingConfirmedEvent for booking ${event.bookingId}" }

        try {
            emailService.sendConfirmationEmail(event.bookingId, event.locale)
        } catch (e: Exception) {
            // Log but don't rethrow - email failure should not affect booking
            logger.error(e) { "Failed to send confirmation email for booking ${event.bookingId}" }
        }
    }
}
