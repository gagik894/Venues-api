package app.venues.platform.webhook

import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatClosedEvent
import app.venues.booking.event.TableOpenedEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.*

class BookingEventListenerTest {

    private val webhookService: WebhookService = mockk(relaxed = true)
    private val listener = BookingEventListener(webhookService)

    @Test
    fun `seat closed maps to notifySeatClosed`() {
        val sessionId = UUID.randomUUID()
        listener.handleSeatClosed(SeatClosedEvent(sessionId, "A1"))

        verify { webhookService.notifySeatClosed(sessionId, "A1") }
    }

    @Test
    fun `GA availability changed maps to notifyGAAvailabilityChanged with reduced payload`() {
        val sessionId = UUID.randomUUID()
        listener.handleGAAvailabilityChanged(
            GAAvailabilityChangedEvent(
                sessionId = sessionId,
                levelIdentifier = "GA1",
                levelName = "ignored-name",
                availableTickets = 42,
                totalCapacity = 100
            )
        )

        verify { webhookService.notifyGAAvailabilityChanged(sessionId, "GA1", 42) }
    }

    @Test
    fun `table opened maps to notifyTableOpened without name`() {
        val sessionId = UUID.randomUUID()
        listener.handleTableOpened(
            TableOpenedEvent(
                sessionId = sessionId,
                tableIdentifier = "T1"
            )
        )

        verify { webhookService.notifyTableOpened(sessionId, "T1") }
    }
}

