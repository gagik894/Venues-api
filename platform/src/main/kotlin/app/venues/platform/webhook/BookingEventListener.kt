package app.venues.platform.webhook

import app.venues.booking.event.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Event listener for booking events that triggers webhook notifications.
 *
 * Listens to events published by the booking module and sends
 * webhook notifications to all registered platforms.
 */
@Component
class BookingEventListener(
    private val webhookService: WebhookService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle seat reserved event
     */
    @Async
    @EventListener
    fun handleSeatClosed(event: SeatClosedEvent) {
        logger.debug { "Received SeatClosedEvent: ${event.seatIdentifier}" }

        webhookService.notifySeatClosed(
            sessionId = event.sessionId,
            seatIdentifier = event.seatIdentifier
        )
    }

    /**
     * Handle seat released event
     */
    @Async
    @EventListener
    fun handleSeatOpened(event: SeatOpenedEvent) {
        logger.debug { "Received SeatOpenedEvent: ${event.seatIdentifier}" }

        webhookService.notifySeatOpened(
            sessionId = event.sessionId,
            seatIdentifier = event.seatIdentifier,
        )
    }

    /**
     * Handle GA availability changed event
     */
    @Async
    @EventListener
    fun handleGAAvailabilityChanged(event: GAAvailabilityChangedEvent) {
        logger.debug { "Received GAAvailabilityChangedEvent: ${event.levelIdentifier}" }

        webhookService.notifyGAAvailabilityChanged(
            sessionId = event.sessionId,
            levelIdentifier = event.levelIdentifier,
            availableTickets = event.availableTickets
        )
    }

    /**
     * Handle table reserved event
     */
    @Async
    @EventListener
    fun handleTableClosed(event: TableClosedEvent) {
        logger.debug { "Received TableClosedEvent: ${event.tableIdentifier}" }

        webhookService.notifyTableClosed(
            sessionId = event.sessionId,
            tableIdentifier = event.tableIdentifier
        )
    }

    /**
     * Handle table released event
     */
    @Async
    @EventListener
    fun handleTableOpened(event: TableOpenedEvent) {
        logger.debug { "Received TableOpenedEvent: ${event.tableIdentifier}" }

        webhookService.notifyTableOpened(
            sessionId = event.sessionId,
            tableIdentifier = event.tableIdentifier
        )
    }
}

