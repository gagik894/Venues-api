package app.venues.booking.api.dto

import app.venues.shared.money.MoneyAmount
import java.time.LocalDate
import java.util.*

/** High-level scope identifier for stats responses. */
data class StatsScope(
    val type: StatsScopeType,
    val id: UUID,
    val referenceIds: List<UUID> = emptyList()
)

enum class StatsScopeType {
    EVENT,
    SESSION
}

data class BestPerformer(
    val name: String,
    val count: Long,
    val totalRevenue: MoneyAmount
)

data class BestPerformers(
    val seating: List<BestPerformer>,
    val templates: List<BestPerformer>,
    val promoCodes: List<BestPerformer>,
    val platforms: List<BestPerformer>,
    val days: List<BestPerformer>
)

data class OverviewStats(
    val totalTickets: Long,
    val soldTickets: Long,
    val pendingOrders: Long,
    val totalRevenue: MoneyAmount,
    val totalPossibleRevenue: MoneyAmount,
    val bestPerformers: BestPerformers
)

data class TemplateStats(
    val totalTickets: Long,
    val soldTickets: Long,
    val availableTickets: Long,
    val closedSeats: Long,
    val totalRevenue: MoneyAmount,
    val presentUsers: Long,
    val color: String?
)

data class ZoneStats(
    val totalTickets: Long,
    val soldTickets: Long,
    val availableTickets: Long,
    val closedSeats: Long,
    val totalRevenue: MoneyAmount,
    val seating: Map<String, ZoneStats> = emptyMap()
)

data class PlatformStats(
    val soldTickets: Long,
    val totalRevenue: MoneyAmount,
    val totalPromoLoss: MoneyAmount,
    val templates: Map<String, TemplateStats>,
    val seating: Map<String, ZoneStats>,
    val totalTickets: Long,
    val totalPossibleRevenue: MoneyAmount
)

data class PromoCodeStats(
    val totalTickets: Long,
    val soldTickets: Long,
    val totalRevenue: MoneyAmount,
    val totalPromoLoss: MoneyAmount
)

data class DayStats(
    val soldTickets: Long,
    val totalRevenue: MoneyAmount,
    val totalPromoLoss: MoneyAmount,
    val levels: Map<String, ZoneStats>,
    val templates: Map<String, TemplateStats>,
    val promoCodes: Map<String, PromoCodeStats>,
    val platforms: Map<String, PlatformStats>
)

data class AttendanceStats(
    val soldTickets: Long,
    val presentUsers: Long,
    val presentOnTimeUsers: Long,
    val presentLateUsers: Long,
    val absentUsers: Long,
    val attendanceRate: String,
    val seating: Map<String, ZoneStats>,
    val templates: Map<String, TemplateStats>
)

data class EventStatsResponse(
    val scope: StatsScope,
    val currency: String,
    val overview: OverviewStats,
    val platforms: Map<String, PlatformStats>,
    val promoCodes: Map<String, PromoCodeStats>,
    val days: Map<LocalDate, DayStats>,
    val attendance: AttendanceStats
)
