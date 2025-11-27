package app.venues.event.service

import app.venues.event.domain.Event
import app.venues.event.domain.EventSession
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.seating.api.SeatingApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class SessionCapacityServiceTest {

    private lateinit var seatingApi: SeatingApi
    private lateinit var gaConfigRepository: SessionGAConfigRepository
    private lateinit var eventSessionRepository: EventSessionRepository
    private lateinit var service: SessionCapacityService

    @BeforeEach
    fun setup() {
        seatingApi = mockk()
        gaConfigRepository = mockk()
        eventSessionRepository = mockk()
        service = SessionCapacityService(seatingApi, gaConfigRepository, eventSessionRepository)
    }

    @Test
    fun `recalculateForSession sets seats plus GA capacity`() {
        val chartId = UUID.randomUUID()
        val event = Event(title = "Show", venueId = UUID.randomUUID(), currency = "AMD")
        event.seatingChartId = chartId
        val session = EventSession(
            event = event,
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600),
            ticketsCount = null,
            priceOverride = null,
            priceRangeOverride = null
        )

        // Optimized: use COUNT query instead of fetching full structure
        every { seatingApi.getSeatCount(chartId) } returns 2
        every { gaConfigRepository.sumCapacityBySessionId(session.id) } returns 50L

        service.recalculateForSession(session)

        assertEquals(52, session.ticketsCount)
    }

    @Test
    fun `recalculateForSession clears tickets when no chart is assigned`() {
        val event = Event(title = "NoChart", venueId = UUID.randomUUID(), currency = "AMD")
        val session = EventSession(
            event = event,
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(1800),
            ticketsCount = 10,
            priceOverride = null,
            priceRangeOverride = null
        )

        service.recalculateForSession(session)

        assertNull(session.ticketsCount)
        verify(exactly = 0) { gaConfigRepository.sumCapacityBySessionId(any()) }
    }
}
