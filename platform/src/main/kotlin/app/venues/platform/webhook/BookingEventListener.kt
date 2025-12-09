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
    fun handleSeatReserved(event: SeatReservedEvent) {
        logger.debug { "Received SeatReservedEvent: ${event.seatIdentifier}" }

        webhookService.notifySeatReserved(
            sessionId = event.sessionId,
            seatIdentifier = event.seatIdentifier
        )
    }

    /**
     * Handle seat released event
     */
    @Async
    @EventListener
    fun handleSeatReleased(event: SeatReleasedEvent) {
        logger.debug { "Received SeatReleasedEvent: ${event.seatIdentifier}" }

        webhookService.notifySeatReleased(
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
    fun handleTableReserved(event: TableReservedEvent) {
        logger.debug { "Received TableReservedEvent: ${event.tableCode}" }

        webhookService.notifyTableReserved(
            sessionId = event.sessionId,
            tableIdentifier = event.tableCode
        )
    }

    /**
     * Handle table released event
     */
    @Async
    @EventListener
    fun handleTableReleased(event: TableReleasedEvent) {
        logger.debug { "Received TableReleasedEvent: ${event.tableCode}" }

        webhookService.notifyTableReleased(
            sessionId = event.sessionId,
            tableIdentifier = event.tableCode
        )
    }
}

