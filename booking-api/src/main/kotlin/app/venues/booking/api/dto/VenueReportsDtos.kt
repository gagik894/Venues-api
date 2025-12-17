package app.venues.booking.api.dto

import app.venues.shared.money.MoneyAmount
import java.time.LocalDate

data class ReportsOverview(
    val totalOrders: Long,
    val totalRevenue: MoneyAmount,
    val totalTicketsSold: Long,
    val currency: String
)

data class ReportsByDate(
    val date: LocalDate,
    val orders: Long,
    val revenue: MoneyAmount,
    val ticketsSold: Long
)

data class ReportsByPlatform(
    val platform: String,
    val orders: Long,
    val revenue: MoneyAmount,
    val ticketsSold: Long
)

data class ReportsData(
    val currency: String,
    val overview: ReportsOverview,
    val byDate: List<ReportsByDate>,
    val byPlatform: List<ReportsByPlatform>
)
