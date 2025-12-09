package app.venues.platform.webhook

import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatReservedEvent
import app.venues.booking.event.TableReleasedEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.*

class BookingEventListenerTest {

    private val webhookService: WebhookService = mockk(relaxed = true)
    private val listener = BookingEventListener(webhookService)

    @Test
    fun `seat reserved maps to notifySeatReserved`() {
        val sessionId = UUID.randomUUID()
        listener.handleSeatReserved(SeatReservedEvent(sessionId, "A1"))

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
    fun `table released maps to notifyTableReleased without name`() {
        val sessionId = UUID.randomUUID()
        listener.handleTableReleased(
            TableReleasedEvent(
                sessionId = sessionId,
                tableId = 1L,
                tableName = "ignored-name",
                tableCode = "T1"
            )
        )

        verify { webhookService.notifyTableOpened(sessionId, "T1") }
    }
}

