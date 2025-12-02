package app.venues.booking.api.dto

import java.math.BigDecimal
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
    val totalRevenue: BigDecimal
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
    val totalRevenue: BigDecimal,
    val totalPossibleRevenue: BigDecimal,
    val bestPerformers: BestPerformers
)

data class TemplateStats(
    val totalTickets: Long,
    val soldTickets: Long,
    val availableTickets: Long,
    val closedSeats: Long,
    val totalRevenue: BigDecimal,
    val presentUsers: Long,
    val color: String?
)

data class ZoneStats(
    val totalTickets: Long,
    val soldTickets: Long,
    val availableTickets: Long,
    val closedSeats: Long,
    val totalRevenue: BigDecimal,
    val seating: Map<String, ZoneStats> = emptyMap()
)

data class PlatformStats(
    val soldTickets: Long,
    val totalRevenue: BigDecimal,
    val totalPromoLoss: BigDecimal,
    val templates: Map<String, TemplateStats>,
    val seating: Map<String, ZoneStats>,
    val totalTickets: Long,
    val totalPossibleRevenue: BigDecimal
)

data class PromoCodeStats(
    val totalTickets: Long,
    val soldTickets: Long,
    val totalRevenue: BigDecimal,
    val totalPromoLoss: BigDecimal
)

data class DayStats(
    val soldTickets: Long,
    val totalRevenue: BigDecimal,
    val totalPromoLoss: BigDecimal,
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
    val overview: OverviewStats,
    val platforms: Map<String, PlatformStats>,
    val promoCodes: Map<String, PromoCodeStats>,
    val days: Map<LocalDate, DayStats>,
    val attendance: AttendanceStats
)
