package app.venues.booking.service

import app.venues.booking.api.dto.StatsScopeType
import app.venues.booking.repository.*
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.event.api.dto.*
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.SeatInfoDto
import app.venues.ticket.api.TicketAttendanceApi
import app.venues.ticket.api.dto.AttendanceSummaryDto
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class EventStatsServiceTest {

    private lateinit var bookingRepository: BookingRepository
    private lateinit var bookingStatisticsRepository: BookingStatisticsRepository
    private lateinit var eventApi: EventApi
    private lateinit var seatingApi: SeatingApi
    private lateinit var ticketAttendanceApi: TicketAttendanceApi

    private lateinit var service: EventStatsService

    @BeforeEach
    fun setUp() {
        bookingRepository = mockk()
        bookingStatisticsRepository = mockk()
        eventApi = mockk()
        seatingApi = mockk()
        ticketAttendanceApi = mockk()

        service = EventStatsService(
            bookingRepository,
            bookingStatisticsRepository,
            eventApi,
            seatingApi,
            ticketAttendanceApi
        )
    }

    @Test
    fun `getSessionStats aggregates overview and attendance data`() {
        val sessionId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val venueId = UUID.randomUUID()

        val sessionInfo = EventSessionDto(
            sessionId = sessionId,
            eventId = eventId,
            venueId = venueId,
            eventTitle = "Jazz Night",
            eventDescription = null,
            currency = "USD",
            startTime = Instant.parse("2025-01-01T19:00:00Z"),
            endTime = Instant.parse("2025-01-01T22:00:00Z")
        )

        val inventory = SessionInventoryResponse(
            sessionId = sessionId,
            eventId = eventId,
            seatingChartId = UUID.randomUUID(),
            seats = mapOf(
                10L to SeatStateDto(
                    status = "S",
                    price = 5_000,
                    color = "#F00",
                    templateName = "VIP"
                )
            ),
            tables = emptyMap(),
            gaAreas = emptyMap(),
            priceTemplates = listOf(
                InventoryPriceTemplateDto(
                    id = UUID.randomUUID(),
                    templateName = "VIP",
                    color = "#F00",
                    price = 5_000,
                    isOverride = false
                )
            ),
            stats = InventoryStatsDto(
                totalSeats = 1,
                availableSeats = 0,
                reservedSeats = 0,
                soldSeats = 1,
                blockedSeats = 0,
                totalGaCapacity = 0,
                availableGaCapacity = 0
            )
        )

        every { eventApi.getEventSessionInfo(sessionId) } returns sessionInfo
        every { eventApi.getSessionInventory(sessionId) } returns inventory

        every { bookingRepository.countPendingBySessionIds(listOf(sessionId)) } returns 2

        every { bookingStatisticsRepository.aggregateSessionSales(listOf(sessionId)) } returns listOf(
            SessionSalesRow(sessionId, 1, BigDecimal("50.00"))
        )
        every { bookingStatisticsRepository.aggregateTemplateSales(listOf(sessionId)) } returns listOf(
            TemplateSalesRow("VIP", 1, BigDecimal("50.00"))
        )
        every { bookingStatisticsRepository.aggregatePromoStats(listOf(sessionId)) } returns emptyList()
        every { bookingStatisticsRepository.aggregatePlatformStats(listOf(sessionId)) } returns emptyList()
        every { bookingStatisticsRepository.aggregateDayStats(listOf(sessionId)) } returns emptyList()
        every { bookingStatisticsRepository.aggregateSeatSales(listOf(sessionId)) } returns listOf(
            SeatSalesRow(10L, "VIP", 1, BigDecimal("50.00"))
        )
        every { bookingStatisticsRepository.aggregateGaSales(listOf(sessionId)) } returns emptyList()
        every { bookingStatisticsRepository.aggregateTableSales(listOf(sessionId)) } returns emptyList()

        every {
            ticketAttendanceApi.getAttendanceSummaries(match { requests ->
                requests.size == 1 &&
                        requests.first().sessionId == sessionId &&
                        requests.first().startTime == sessionInfo.startTime
            })
        } returns listOf(
            AttendanceSummaryDto(
                sessionId = sessionId,
                soldTickets = 1,
                presentUsers = 1,
                presentOnTimeUsers = 1,
                presentLateUsers = 0,
                absentUsers = 0
            )
        )

        every { seatingApi.getSeatInfoBatch(listOf(10L)) } returns listOf(
            SeatInfoDto(
                id = 10L,
                code = "A-10",
                seatNumber = "10",
                rowLabel = "A",
                zoneId = 1L,
                zoneName = "VIP Zone",
                categoryKey = "VIP"
            )
        )

        val response = service.getSessionStats(sessionId, venueId)

        assertEquals(StatsScopeType.SESSION, response.scope.type)
        assertEquals("USD", response.currency)
        assertEquals(1L, response.overview.totalTickets)
        assertEquals(1L, response.overview.soldTickets)
        assertEquals(BigDecimal("50.00"), response.overview.totalRevenue.amount)
        assertEquals(BigDecimal("50.00"), response.overview.totalPossibleRevenue.amount)
        assertEquals(2L, response.overview.pendingOrders)
        assertEquals("100.00%", response.attendance.attendanceRate)
        assertEquals(1L, response.attendance.presentUsers)
        assertEquals(1, response.attendance.seating.size)
        assertEquals("VIP Zone", response.attendance.seating.keys.first())
    }

    @Test
    fun `getEventStats fails when event has no sessions`() {
        val eventId = UUID.randomUUID()
        val venueId = UUID.randomUUID()

        every { eventApi.getSessionIdsForEvent(eventId) } returns emptyList()

        assertThrows(VenuesException.ResourceNotFound::class.java) {
            service.getEventStats(eventId, venueId)
        }
    }

    @Test
    fun `throws when sessions have mixed currencies`() {
        val eventId = UUID.randomUUID()
        val venueId = UUID.randomUUID()
        val sessionIds = listOf(UUID.randomUUID(), UUID.randomUUID())

        every { eventApi.getSessionIdsForEvent(eventId) } returns sessionIds
        every { eventApi.getEventSessionInfo(sessionIds[0]) } returns EventSessionDto(
            sessionId = sessionIds[0],
            eventId = eventId,
            venueId = venueId,
            eventTitle = "Multi",
            eventDescription = null,
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now()
        )
        every { eventApi.getEventSessionInfo(sessionIds[1]) } returns EventSessionDto(
            sessionId = sessionIds[1],
            eventId = eventId,
            venueId = venueId,
            eventTitle = "Multi",
            eventDescription = null,
            currency = "EUR",
            startTime = Instant.now(),
            endTime = Instant.now()
        )

        assertThrows(VenuesException.ValidationFailure::class.java) {
            service.getEventStats(eventId, venueId)
        }
    }

    private data class SessionSalesRow(
        private val sessionId: UUID,
        private val ticketsSold: Long,
        private val totalRevenue: BigDecimal
    ) : SessionSalesProjection {
        override fun getSessionId(): UUID = sessionId
        override fun getTicketsSold(): Long = ticketsSold
        override fun getTotalRevenue(): BigDecimal = totalRevenue
    }

    private data class TemplateSalesRow(
        private val templateName: String,
        private val soldTickets: Long,
        private val totalRevenue: BigDecimal
    ) : TemplateSalesProjection {
        override fun getTemplateName(): String = templateName
        override fun getSoldTickets(): Long = soldTickets
        override fun getTotalRevenue(): BigDecimal = totalRevenue
    }

    private data class SeatSalesRow(
        private val seatId: Long,
        private val templateName: String,
        private val soldTickets: Long,
        private val totalRevenue: BigDecimal
    ) : SeatSalesProjection {
        override fun getSeatId(): Long = seatId
        override fun getTemplateName(): String = templateName
        override fun getSoldTickets(): Long = soldTickets
        override fun getTotalRevenue(): BigDecimal = totalRevenue
    }
}