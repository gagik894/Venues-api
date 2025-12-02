package app.venues.booking.api.dto

import java.math.BigDecimal
import java.time.LocalDate

data class ReportsOverview(
    val totalOrders: Long,
    val totalRevenue: BigDecimal,
    val totalTicketsSold: Long
)

data class ReportsByDate(
    val date: LocalDate,
    val orders: Long,
    val revenue: BigDecimal,
    val ticketsSold: Long
)

data class ReportsByPlatform(
    val platform: String,
    val orders: Long,
    val revenue: BigDecimal,
    val ticketsSold: Long
)

data class ReportsData(
    val overview: ReportsOverview,
    val byDate: List<ReportsByDate>,
    val byPlatform: List<ReportsByPlatform>
)
