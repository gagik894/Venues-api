package app.venues.booking.service

import app.venues.booking.api.dto.ReportsByDate
import app.venues.booking.api.dto.ReportsByPlatform
import app.venues.booking.api.dto.ReportsData
import app.venues.booking.api.dto.ReportsOverview
import app.venues.booking.repository.BookingStatisticsRepository
import app.venues.booking.service.util.PlatformKeyFormatter
import app.venues.common.exception.VenuesException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

@Service
class VenueReportsService(
    private val bookingStatisticsRepository: BookingStatisticsRepository
) {

    private val logger = KotlinLogging.logger {}

    fun getVenueReports(venueId: UUID, startDate: LocalDate, endDate: LocalDate): ReportsData {
        if (startDate.isAfter(endDate)) {
            throw VenuesException.ValidationFailure("startDate must be on or before endDate")
        }

        val startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val overviewProjection = bookingStatisticsRepository.aggregateVenueOverview(venueId, startInstant, endInstant)
        val overview = ReportsOverview(
            totalOrders = overviewProjection?.getOrders() ?: 0,
            totalRevenue = overviewProjection?.getRevenue() ?: BigDecimal.ZERO,
            totalTicketsSold = overviewProjection?.getTicketsSold() ?: 0
        )

        val byDate = bookingStatisticsRepository
            .aggregateVenueByDate(venueId, startInstant, endInstant)
            .map { projection ->
                ReportsByDate(
                    date = projection.getBookingDate(),
                    orders = projection.getOrders(),
                    revenue = projection.getRevenue(),
                    ticketsSold = projection.getTicketsSold()
                )
            }

        val byPlatform = bookingStatisticsRepository
            .aggregateVenueByPlatform(venueId, startInstant, endInstant)
            .map { projection ->
                ReportsByPlatform(
                    platform = PlatformKeyFormatter.format(projection.getSalesChannel(), projection.getPlatformId()),
                    orders = projection.getOrders(),
                    revenue = projection.getRevenue(),
                    ticketsSold = projection.getTicketsSold()
                )
            }

        logger.debug {
            "Generated venue report for $venueId from $startDate to $endDate with ${overview.totalOrders} orders"
        }

        return ReportsData(
            overview = overview,
            byDate = byDate,
            byPlatform = byPlatform
        )
    }
}
