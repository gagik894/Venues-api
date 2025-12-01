package app.venues.booking.event

import app.venues.booking.service.BookingConfirmationEmailService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Async event listener for booking-related email notifications.
 *
 * Uses @TransactionalEventListener to ensure email is only sent after
 * the booking transaction commits successfully.
 *
 * Benefits:
 * - Non-blocking: API responses are not delayed by email sending
 * - Fault-tolerant: Email failures don't affect booking confirmation
 * - Scalable: Email sending can be scaled independently
 * - Transaction-safe: No email sent if transaction rolls back
 */
@Component
class BookingEmailEventListener(
    private val emailService: BookingConfirmationEmailService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle booking confirmed event - send confirmation email with tickets.
     *
     * Runs asynchronously after transaction commits.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
