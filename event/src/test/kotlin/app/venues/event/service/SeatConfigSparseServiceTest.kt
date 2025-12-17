package app.venues.event.service

import app.venues.event.domain.*
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.SeatInfoDto
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class SeatConfigSparseServiceTest {

    private lateinit var eventSessionRepository: EventSessionRepository
    private lateinit var sessionSeatConfigRepository: SessionSeatConfigRepository
    private lateinit var seatingApi: SeatingApi
    private lateinit var service: SeatConfigSparseService

    @BeforeEach
    fun setup() {
        eventSessionRepository = mockk(relaxed = true)
        sessionSeatConfigRepository = mockk()
        seatingApi = mockk()
        service = SeatConfigSparseService(eventSessionRepository, sessionSeatConfigRepository, seatingApi)
    }

    @Test
    fun `purgeDefaultRows removes redundant configs`() {
        val sessionId = UUID.randomUUID()
        val event = Event(title = "Show", venueId = UUID.randomUUID())
        val template = EventPriceTemplate(event = event, templateName = "VIP", price = BigDecimal("100.00"))
        event.priceTemplates.add(template)
        val session = EventSession(
            event = event,
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )
        val config = SessionSeatConfig(
            session = session,
            seatId = 10L,
            priceTemplate = template,
            status = ConfigStatus.AVAILABLE
        )

        every { sessionSeatConfigRepository.findBySessionIdAndSeatIdIn(sessionId, listOf(10L)) } returns listOf(config)
        every { seatingApi.getSeatInfoBatch(listOf(10L)) } returns listOf(
            SeatInfoDto(
                id = 10L,
                code = "A-10",
                seatNumber = "10",
                rowLabel = "A",
                zoneId = 1L,
                zoneName = "Zone",
                categoryKey = "VIP"
            )
        )
        every { sessionSeatConfigRepository.deleteAllInBatch(listOf(config)) } just Runs

        service.purgeDefaultRows(sessionId, listOf(10L))

        verify { sessionSeatConfigRepository.deleteAllInBatch(listOf(config)) }
    }

    @Test
    fun `purgeDefaultRows keeps configs for non default or unavailable seats`() {
        val sessionId = UUID.randomUUID()
        val event = Event(title = "Show", venueId = UUID.randomUUID())
        val defaultTemplate = EventPriceTemplate(event = event, templateName = "VIP", price = BigDecimal("100.00"))
        val customTemplate = EventPriceTemplate(event = event, templateName = "Custom", price = BigDecimal("150.00"))
        event.priceTemplates.add(defaultTemplate)
        val session = EventSession(
            event = event,
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )
        val config = SessionSeatConfig(
            session = session,
            seatId = 11L,
            priceTemplate = customTemplate,
            status = ConfigStatus.AVAILABLE
        )

        every { sessionSeatConfigRepository.findBySessionIdAndSeatIdIn(sessionId, listOf(11L)) } returns listOf(config)
        every { seatingApi.getSeatInfoBatch(listOf(11L)) } returns listOf(
            SeatInfoDto(
                id = 11L,
                code = "B-11",
                seatNumber = "11",
                rowLabel = "B",
                zoneId = 1L,
                zoneName = "Zone",
                categoryKey = "VIP"
            )
        )

        service.purgeDefaultRows(sessionId, listOf(11L))

        verify(exactly = 0) { sessionSeatConfigRepository.deleteAllInBatch(listOf(config)) }
    }
}
