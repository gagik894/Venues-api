package app.venues.booking.service

import app.venues.booking.api.dto.ReportsData
import app.venues.booking.repository.BookingStatisticsRepository
import app.venues.booking.repository.VenueDateProjection
import app.venues.booking.repository.VenueOverviewProjection
import app.venues.booking.repository.VenuePlatformProjection
import app.venues.common.exception.VenuesException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class VenueReportsServiceTest {

    private val bookingStatisticsRepository: BookingStatisticsRepository = mockk()
    private val service = VenueReportsService(bookingStatisticsRepository)

    @Test
    fun `aggregates venue report data`() {
        val venueId = UUID.randomUUID()
        val startDate = LocalDate.parse("2025-01-01")
        val endDate = LocalDate.parse("2025-01-07")

        every {
            bookingStatisticsRepository.aggregateVenueOverview(any(), any(), any())
        } returns OverviewStub(orders = 5, revenue = BigDecimal("250.00"), tickets = 20)

        every {
            bookingStatisticsRepository.findVenueCurrencies(any(), any(), any())
        } returns listOf("AMD")

        every {
            bookingStatisticsRepository.aggregateVenueByDate(any(), any(), any())
        } returns listOf(
            DateStub(LocalDate.parse("2025-01-02"), 2, BigDecimal("100.00"), 8),
            DateStub(LocalDate.parse("2025-01-03"), 3, BigDecimal("150.00"), 12)
        )

        every {
            bookingStatisticsRepository.aggregateVenueByPlatform(any(), any(), any())
        } returns listOf(
            PlatformStub("WEB", null, 3, BigDecimal("150.00"), 12),
            PlatformStub(
                "PLATFORM",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                2,
                BigDecimal("100.00"),
                8
            )
        )

        val result: ReportsData = service.getVenueReports(venueId, startDate, endDate)

        assertEquals(5, result.overview.totalOrders)
        assertEquals(BigDecimal("250.00"), result.overview.totalRevenue)
        assertEquals("AMD", result.currency)
        assertEquals("AMD", result.overview.currency)
        assertEquals(2, result.byDate.size)
        assertEquals("WEB", result.byPlatform.first().platform)
        assertEquals("11111111-1111-1111-1111-111111111111", result.byPlatform.last().platform)
    }

    @Test
    fun `throws when start date is after end date`() {
        val venueId = UUID.randomUUID()
        val startDate = LocalDate.parse("2025-02-01")
        val endDate = LocalDate.parse("2025-01-01")

        assertThrows(VenuesException.ValidationFailure::class.java) {
            service.getVenueReports(venueId, startDate, endDate)
        }
    }

    @Test
    fun `throws when multiple currencies detected`() {
        val venueId = UUID.randomUUID()
        val startDate = LocalDate.parse("2025-01-01")
        val endDate = LocalDate.parse("2025-01-02")

        every {
            bookingStatisticsRepository.findVenueCurrencies(any(), any(), any())
        } returns listOf("USD", "EUR")

        assertThrows(VenuesException.ValidationFailure::class.java) {
            service.getVenueReports(venueId, startDate, endDate)
        }
    }

    private data class OverviewStub(
        private val orders: Long,
        private val revenue: BigDecimal,
        private val tickets: Long
    ) : VenueOverviewProjection {
        override fun getOrders(): Long = orders
        override fun getRevenue(): BigDecimal = revenue
        override fun getTicketsSold(): Long = tickets
    }

    private data class DateStub(
        private val date: LocalDate,
        private val orders: Long,
        private val revenue: BigDecimal,
        private val tickets: Long
    ) : VenueDateProjection {
        override fun getBookingDate(): LocalDate = date
        override fun getOrders(): Long = orders
        override fun getRevenue(): BigDecimal = revenue
        override fun getTicketsSold(): Long = tickets
    }

    private data class PlatformStub(
        private val channel: String,
        private val platformId: UUID?,
        private val orders: Long,
        private val revenue: BigDecimal,
        private val tickets: Long
    ) : VenuePlatformProjection {
        override fun getSalesChannel(): String = channel
        override fun getPlatformId(): UUID? = platformId
        override fun getOrders(): Long = orders
        override fun getRevenue(): BigDecimal = revenue
        override fun getTicketsSold(): Long = tickets
    }
}
