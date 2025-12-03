package app.venues.booking.service

import app.venues.booking.api.dto.*
import app.venues.booking.repository.*
import app.venues.booking.service.util.PlatformKeyFormatter
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSessionDto
import app.venues.event.api.dto.SessionInventoryResponse
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.GaInfoDto
import app.venues.seating.api.dto.SeatInfoDto
import app.venues.seating.api.dto.SeatingChartStructureDto
import app.venues.seating.api.dto.TableInfoDto
import app.venues.shared.money.toMoney
import app.venues.ticket.api.TicketAttendanceApi
import app.venues.ticket.api.dto.AttendanceRequestDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class EventStatsService(
    private val bookingRepository: BookingRepository,
    private val bookingStatisticsRepository: BookingStatisticsRepository,
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi,
    private val ticketAttendanceApi: TicketAttendanceApi
) {

    private val logger = KotlinLogging.logger {}

    private val chartIndexCache = ConcurrentHashMap<UUID, ChartIndex>()
    private val seatCache = ConcurrentHashMap<Long, SeatInfoDto>()
    private val gaCache = ConcurrentHashMap<Long, GaInfoDto>()
    private val tableCache = ConcurrentHashMap<Long, TableInfoDto>()

    @Transactional(readOnly = true)
    fun getEventStats(eventId: UUID, venueId: UUID): EventStatsResponse {
        val sessionIds = eventApi.getSessionIdsForEvent(eventId)
        if (sessionIds.isEmpty()) {
            throw VenuesException.ResourceNotFound("Event $eventId has no sessions")
        }
        val sessionInfoMap = sessionIds.associateWith { sessionId ->
            eventApi.getEventSessionInfo(sessionId)
                ?: throw VenuesException.ResourceNotFound("Session $sessionId not found")
        }
        ensureVenueOwnership(sessionInfoMap.values.toList(), venueId)
        return buildStats(sessionIds, StatsScope(StatsScopeType.EVENT, eventId, sessionIds), sessionInfoMap)
    }

    @Transactional(readOnly = true)
    fun getSessionStats(sessionId: UUID, venueId: UUID): EventStatsResponse {
        val sessionInfo = eventApi.getEventSessionInfo(sessionId)
            ?: throw VenuesException.ResourceNotFound("Session $sessionId not found")
        if (sessionInfo.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Session does not belong to venue $venueId")
        }
        return buildStats(
            listOf(sessionId),
            StatsScope(StatsScopeType.SESSION, sessionId, listOf(sessionId)),
            mapOf(sessionId to sessionInfo)
        )
    }

    private fun ensureVenueOwnership(sessionInfos: List<EventSessionDto>, venueId: UUID) {
        val mismatched = sessionInfos.firstOrNull { it.venueId != venueId }
        if (mismatched != null) {
            throw VenuesException.AuthorizationFailure("Session ${mismatched.sessionId} does not belong to venue $venueId")
        }
    }

    private fun buildStats(
        sessionIds: List<UUID>,
        scope: StatsScope,
        sessionInfoMap: Map<UUID, EventSessionDto>
    ): EventStatsResponse {
        val currencies = sessionInfoMap.values.map { it.currency }.distinct()
        if (currencies.size > 1) {
            throw VenuesException.ValidationFailure("Session currencies do not match for stats scope ${scope.type}")
        }
        val currency = currencies.first()

        val inventories = sessionIds.associateWith { sessionId ->
            eventApi.getSessionInventory(sessionId)
                ?: throw VenuesException.ResourceNotFound("Inventory snapshot missing for session $sessionId")
        }

        val sessionContexts = inventories.mapValues { (_, inventory) ->
            val chartIndex = getChartIndex(inventory.seatingChartId)
            SessionInventoryContext(
                inventory = inventory,
                chartIndex = chartIndex,
                seatStates = materializeSeatStates(inventory, chartIndex),
                templatePrices = buildTemplatePriceLookup(inventory)
            )
        }

        val sessionSales = bookingStatisticsRepository.aggregateSessionSales(sessionIds)
        val templateSales = bookingStatisticsRepository.aggregateTemplateSales(sessionIds)
        val promoStats = bookingStatisticsRepository.aggregatePromoStats(sessionIds)
        val platformStats = bookingStatisticsRepository.aggregatePlatformStats(sessionIds)
        val dayStats = bookingStatisticsRepository.aggregateDayStats(sessionIds)
        val seatSales = bookingStatisticsRepository.aggregateSeatSales(sessionIds)
        val gaSales = bookingStatisticsRepository.aggregateGaSales(sessionIds)
        val tableSales = bookingStatisticsRepository.aggregateTableSales(sessionIds)

        val totalRevenue = sessionSales.fold(BigDecimal.ZERO) { acc, row -> acc + row.getTotalRevenue() }
        val soldTickets = sessionSales.fold(0L) { acc, row -> acc + row.getTicketsSold() }
        val pendingOrders = bookingRepository.countPendingBySessionIds(sessionIds)

        val (totalTickets, totalPossibleRevenue) = computeCapacityAndPotential(sessionContexts.values)

        val zoneStats = buildZoneStats(sessionContexts.values, seatSales, gaSales, tableSales, currency)
        val templateStats = buildTemplateStats(sessionContexts.values, templateSales, currency)
        val bestPerformers =
            buildBestPerformers(zoneStats, templateStats, promoStats, platformStats, dayStats, currency)

        val promoCodeMap = promoStats
            .filter { it.getPromoCode() != "NONE" }
            .associate { projection ->
                projection.getPromoCode() to PromoCodeStats(
                    totalTickets = projection.getSoldTickets(),
                    soldTickets = projection.getSoldTickets(),
                    totalRevenue = projection.getTotalRevenue().toMoney(currency),
                    totalPromoLoss = projection.getTotalPromoLoss().toMoney(currency)
                )
            }

        val platformMap = platformStats.associate { projection ->
            val key = PlatformKeyFormatter.format(projection.getSalesChannel(), projection.getPlatformId())
            key to PlatformStats(
                soldTickets = projection.getSoldTickets(),
                totalRevenue = projection.getTotalRevenue().toMoney(currency),
                totalPromoLoss = projection.getTotalPromoLoss().toMoney(currency),
                templates = emptyMap(),
                seating = emptyMap(),
                totalTickets = projection.getSoldTickets(),
                totalPossibleRevenue = projection.getTotalRevenue().add(projection.getTotalPromoLoss())
                    .toMoney(currency)
            )
        }

        val dayMap = dayStats.associate { projection ->
            projection.getBookingDate() to DayStats(
                soldTickets = projection.getSoldTickets(),
                totalRevenue = projection.getTotalRevenue().toMoney(currency),
                totalPromoLoss = projection.getTotalPromoLoss().toMoney(currency),
                levels = emptyMap(),
                templates = emptyMap(),
                promoCodes = emptyMap(),
                platforms = emptyMap()
            )
        }

        val attendance = buildAttendanceStats(sessionInfoMap, templateStats, zoneStats)

        val overview = OverviewStats(
            totalTickets = totalTickets,
            soldTickets = soldTickets,
            pendingOrders = pendingOrders,
            totalRevenue = totalRevenue.toMoney(currency),
            totalPossibleRevenue = totalPossibleRevenue.toMoney(currency),
            bestPerformers = bestPerformers
        )

        return EventStatsResponse(
            scope = scope,
            currency = currency,
            overview = overview,
            platforms = platformMap,
            promoCodes = promoCodeMap,
            days = dayMap,
            attendance = attendance
        )
    }

    private fun computeCapacityAndPotential(
        contexts: Collection<SessionInventoryContext>
    ): Pair<Long, BigDecimal> {
        var totalTickets = 0L
        var totalPotential = BigDecimal.ZERO
        contexts.forEach { context ->
            totalTickets += context.chartIndex.seatInfo.size
            totalTickets += context.inventory.stats.totalGaCapacity
            totalPotential = totalPotential
                .add(sumSeatPotential(context))
                .add(sumGaPotential(context))
        }
        return totalTickets to totalPotential
    }

    private fun sumSeatPotential(context: SessionInventoryContext): BigDecimal {
        val missingTemplates = mutableSetOf<String>()
        return context.seatStates.fold(BigDecimal.ZERO) { acc, seat ->
            acc + resolveTemplatePrice(
                seat.templateName,
                context.templatePrices,
                missingTemplates,
                context.inventory.sessionId
            )
        }
    }

    private fun sumGaPotential(context: SessionInventoryContext): BigDecimal {
        val missingTemplates = mutableSetOf<String>()
        return context.inventory.gaAreas.values.fold(BigDecimal.ZERO) { acc, gaState ->
            val totalCapacity = (gaState.available + gaState.soldCount).toLong()
            val price = resolveTemplatePrice(
                gaState.templateName,
                context.templatePrices,
                missingTemplates,
                context.inventory.sessionId
            )
            acc + price.multiply(BigDecimal.valueOf(totalCapacity))
        }
    }

    private fun buildZoneStats(
        contexts: Collection<SessionInventoryContext>,
        seatSales: List<SeatSalesProjection>,
        gaSales: List<GaSalesProjection>,
        tableSales: List<TableSalesProjection>,
        currency: String
    ): Map<String, ZoneStats> {
        val seatRevenueMap = seatSales.associateBy({ it.getSeatId() }, { it.getTotalRevenue() })
        val gaRevenueMap = gaSales.associateBy({ it.getGaAreaId() }, { it.getTotalRevenue() })
        val tableRevenueMap = tableSales.associateBy({ it.getTableId() }, { it.getTotalRevenue() })

        val zoneAccumulator = mutableMapOf<String, MutableZoneBucket>()

        contexts.forEach { context ->
            context.seatStates.forEach { seat ->
                val zoneName = seat.zoneName
                val bucket = zoneAccumulator.computeIfAbsent(zoneName) { MutableZoneBucket() }
                bucket.totalTickets++
                when (seat.status) {
                    "S" -> bucket.soldTickets++
                    "A", "R" -> bucket.availableTickets++
                    "B", "C" -> bucket.closedSeats++
                }
                bucket.totalRevenue = bucket.totalRevenue.add(seatRevenueMap[seat.seatId] ?: BigDecimal.ZERO)
            }

            context.inventory.gaAreas.forEach { (gaId, gaState) ->
                val gaInfo = context.chartIndex.gaInfo[gaId] ?: fetchGaMetadata(gaId)
                val zoneName = gaInfo?.name ?: "GA-$gaId"
                val bucket = zoneAccumulator.computeIfAbsent(zoneName) { MutableZoneBucket() }
                val totalCapacity = gaState.available + gaState.soldCount
                bucket.totalTickets += totalCapacity
                bucket.soldTickets += gaState.soldCount
                bucket.availableTickets += gaState.available
                if (gaState.status == "B") {
                    bucket.closedSeats += gaState.available
                }
                bucket.totalRevenue = bucket.totalRevenue.add(gaRevenueMap[gaId] ?: BigDecimal.ZERO)
            }
        }

        tableSales.forEach { projection ->
            val tableInfo = fetchTableMetadata(projection.getTableId())
            val zoneName = tableInfo?.zoneName ?: "Table-${projection.getTableId()}"
            val bucket = zoneAccumulator.computeIfAbsent(zoneName) { MutableZoneBucket() }
            bucket.totalRevenue = bucket.totalRevenue.add(projection.getTotalRevenue())
        }

        return zoneAccumulator.mapValues { (_, bucket) ->
            bucket.toZoneStats(currency)
        }
    }

    private fun buildTemplateStats(
        contexts: Collection<SessionInventoryContext>,
        templateSales: List<TemplateSalesProjection>,
        currency: String
    ): Map<String, TemplateStats> {
        val revenueMap = templateSales.associateBy({ it.getTemplateName() }, { it.getTotalRevenue() })
        val templateAccumulator = mutableMapOf<String, MutableTemplateBucket>()
        val templateColors = extractTemplateColors(contexts)

        contexts.forEach { context ->
            context.seatStates.forEach { seat ->
                val templateName = seat.templateName ?: "UNASSIGNED"
                val bucket = templateAccumulator.computeIfAbsent(templateName) {
                    MutableTemplateBucket(color = templateColors[templateName])
                }
                bucket.totalTickets++
                when (seat.status) {
                    "S" -> bucket.soldTickets++
                    "A", "R" -> bucket.availableTickets++
                    "B", "C" -> bucket.closedSeats++
                }
            }
            context.inventory.gaAreas.values.forEach { gaState ->
                val templateName = gaState.templateName ?: "UNASSIGNED"
                val bucket = templateAccumulator.computeIfAbsent(templateName) {
                    MutableTemplateBucket(color = templateColors[templateName])
                }
                val totalCapacity = gaState.available + gaState.soldCount
                bucket.totalTickets += totalCapacity
                bucket.soldTickets += gaState.soldCount
                bucket.availableTickets += gaState.available
                if (gaState.status == "B") {
                    bucket.closedSeats += gaState.available
                }
            }
        }

        return templateAccumulator.mapValues { (templateName, bucket) ->
            bucket.totalRevenue = revenueMap[templateName] ?: BigDecimal.ZERO
            bucket.toTemplateStats(currency)
        }
    }

    private fun extractTemplateColors(contexts: Collection<SessionInventoryContext>): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        contexts.forEach { context ->
            context.inventory.priceTemplates.forEach { template ->
                result.putIfAbsent(template.templateName, template.color)
            }
        }
        return result
    }

    private fun buildBestPerformers(
        zones: Map<String, ZoneStats>,
        templates: Map<String, TemplateStats>,
        promoStats: List<PromoCodeStatsProjection>,
        platformStats: List<PlatformStatsProjection>,
        dayStats: List<DayStatsProjection>,
        currency: String
    ): BestPerformers {
        val seatingPerformers = zones.entries
            .sortedByDescending { it.value.soldTickets }
            .take(5)
            .map { (name, stats) -> BestPerformer(name, stats.soldTickets, stats.totalRevenue) }

        val templatePerformers = templates.entries
            .sortedByDescending { it.value.soldTickets }
            .take(5)
            .map { (name, stats) -> BestPerformer(name, stats.soldTickets, stats.totalRevenue) }

        val promoPerformers = promoStats
            .filter { it.getPromoCode() != "NONE" }
            .sortedByDescending { it.getSoldTickets() }
            .take(5)
            .map { projection ->
                BestPerformer(
                    name = projection.getPromoCode(),
                    count = projection.getSoldTickets(),
                    totalRevenue = projection.getTotalRevenue().toMoney(currency)
                )
            }

        val platformPerformers = platformStats
            .sortedByDescending { it.getTotalRevenue() }
            .take(5)
            .map { projection ->
                BestPerformer(
                    name = PlatformKeyFormatter.format(projection.getSalesChannel(), projection.getPlatformId()),
                    count = projection.getSoldTickets(),
                    totalRevenue = projection.getTotalRevenue().toMoney(currency)
                )
            }

        val dayPerformers = dayStats
            .sortedByDescending { it.getSoldTickets() }
            .take(5)
            .map { projection ->
                BestPerformer(
                    name = projection.getBookingDate().toString(),
                    count = projection.getSoldTickets(),
                    totalRevenue = projection.getTotalRevenue().toMoney(currency)
                )
            }

        return BestPerformers(
            seating = seatingPerformers,
            templates = templatePerformers,
            promoCodes = promoPerformers,
            platforms = platformPerformers,
            days = dayPerformers
        )
    }

    private fun buildAttendanceStats(
        sessionInfoMap: Map<UUID, EventSessionDto>,
        templateStats: Map<String, TemplateStats>,
        zoneStats: Map<String, ZoneStats>
    ): AttendanceStats {
        val requests = sessionInfoMap.map { (sessionId, info) ->
            AttendanceRequestDto(sessionId = sessionId, startTime = info.startTime)
        }
        val attendanceSummaries = ticketAttendanceApi.getAttendanceSummaries(requests)
        val totals = attendanceSummaries.fold(AttendanceAccumulator()) { acc, summary ->
            acc.apply {
                sold += summary.soldTickets
                present += summary.presentUsers
                presentOnTime += summary.presentOnTimeUsers
                presentLate += summary.presentLateUsers
                absent += summary.absentUsers
            }
        }

        val attendanceRate = if (totals.sold == 0L) {
            "0%"
        } else {
            val rate = BigDecimal(totals.present)
                .divide(BigDecimal(totals.sold), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
            "${rate.setScale(2, RoundingMode.HALF_UP)}%"
        }

        return AttendanceStats(
            soldTickets = totals.sold,
            presentUsers = totals.present,
            presentOnTimeUsers = totals.presentOnTime,
            presentLateUsers = totals.presentLate,
            absentUsers = totals.absent,
            attendanceRate = attendanceRate,
            seating = zoneStats,
            templates = templateStats
        )
    }

    private fun buildTemplatePriceLookup(inventory: SessionInventoryResponse): Map<String, BigDecimal> {
        // Inventory payload carries pricing by template only; map once per snapshot to avoid per-seat lookups.
        return inventory.priceTemplates.associate { template ->
            template.templateName to template.price.amount
        }
    }

    private fun resolveTemplatePrice(
        templateName: String?,
        templatePrices: Map<String, BigDecimal>,
        missingTemplates: MutableSet<String>,
        sessionId: UUID
    ): BigDecimal {
        if (templateName.isNullOrBlank()) {
            return BigDecimal.ZERO
        }
        val price = templatePrices[templateName]
        if (price == null && missingTemplates.add(templateName)) {
            logger.warn { "Price template '$templateName' missing in inventory for session $sessionId; defaulting to 0" }
        }
        return price ?: BigDecimal.ZERO
    }

    private fun getChartIndex(chartId: UUID): ChartIndex {
        return chartIndexCache.computeIfAbsent(chartId) { id ->
            val structure = seatingApi.getChartStructure(id)
                ?: throw VenuesException.ResourceNotFound("Seating chart $id not found")
            buildChartIndex(structure)
        }
    }

    private fun buildChartIndex(structure: SeatingChartStructureDto): ChartIndex {
        val zoneNameMap = structure.zones.associateBy({ it.id }, { it.name })
        val seatInfo = structure.seats.associate { seat ->
            val zoneName = zoneNameMap[seat.zoneId] ?: "Zone-${seat.zoneId}"
            seat.id to SeatInfoDto(
                id = seat.id,
                code = seat.code,
                seatNumber = seat.seatNumber,
                rowLabel = seat.rowLabel,
                zoneId = seat.zoneId,
                zoneName = zoneName,
                categoryKey = seat.categoryKey
            )
        }
        val gaInfo = structure.gaAreas.associate { ga ->
            val zoneName = zoneNameMap[ga.zoneId] ?: "Zone-${ga.zoneId}"
            ga.id to GaInfoDto(
                id = ga.id,
                code = ga.code,
                name = ga.name,
                capacity = ga.capacity,
                zoneId = ga.zoneId,
                categoryKey = ga.categoryKey
            )
        }
        return ChartIndex(seatInfo = seatInfo, gaInfo = gaInfo)
    }

    private fun materializeSeatStates(
        inventory: SessionInventoryResponse,
        chartIndex: ChartIndex
    ): List<SeatStateView> {
        val seatInfo = chartIndex.seatInfo
        val seatStates = ArrayList<SeatStateView>(seatInfo.size)

        inventory.seats.forEach { (seatId, state) ->
            val info = seatInfo[seatId] ?: fetchSeatInfoFallback(seatId)
            val zoneName = info?.zoneName ?: "Unknown"
            val templateName = state.templateName ?: info?.categoryKey
            seatStates.add(
                SeatStateView(
                    seatId = seatId,
                    status = state.status,
                    templateName = templateName,
                    zoneName = zoneName
                )
            )
        }

        val missingSeatIds = seatInfo.keys - inventory.seats.keys
        missingSeatIds.forEach { seatId ->
            val info = seatInfo[seatId] ?: fetchSeatInfoFallback(seatId) ?: return@forEach
            seatStates.add(
                SeatStateView(
                    seatId = seatId,
                    status = "A",
                    templateName = info.categoryKey,
                    zoneName = info.zoneName
                )
            )
        }

        return seatStates
    }

    private fun fetchSeatInfoFallback(seatId: Long): SeatInfoDto? {
        return seatCache[seatId] ?: seatingApi.getSeatInfo(seatId)?.also { seatCache[seatId] = it }
    }

    private fun fetchGaMetadata(gaId: Long): GaInfoDto? {
        return gaCache[gaId] ?: seatingApi.getGaInfo(gaId)?.also { gaCache[gaId] = it }
    }

    private fun fetchTableMetadata(tableId: Long): TableInfoDto? {
        return tableCache[tableId] ?: seatingApi.getTableInfo(tableId)?.also { tableCache[tableId] = it }
    }
}

private data class SessionInventoryContext(
    val inventory: SessionInventoryResponse,
    val chartIndex: ChartIndex,
    val seatStates: List<SeatStateView>,
    val templatePrices: Map<String, BigDecimal>
)

private data class SeatStateView(
    val seatId: Long,
    val status: String,
    val templateName: String?,
    val zoneName: String
)

private data class ChartIndex(
    val seatInfo: Map<Long, SeatInfoDto>,
    val gaInfo: Map<Long, GaInfoDto>
)

private data class MutableZoneBucket(
    var totalTickets: Long = 0,
    var soldTickets: Long = 0,
    var availableTickets: Long = 0,
    var closedSeats: Long = 0,
    var totalRevenue: BigDecimal = BigDecimal.ZERO
) {
    fun toZoneStats(currency: String): ZoneStats = ZoneStats(
        totalTickets = totalTickets,
        soldTickets = soldTickets,
        availableTickets = availableTickets,
        closedSeats = closedSeats,
        totalRevenue = totalRevenue.toMoney(currency),
        seating = emptyMap()
    )
}

private data class MutableTemplateBucket(
    var totalTickets: Long = 0,
    var soldTickets: Long = 0,
    var availableTickets: Long = 0,
    var closedSeats: Long = 0,
    var totalRevenue: BigDecimal = BigDecimal.ZERO,
    val color: String?
) {
    fun toTemplateStats(currency: String): TemplateStats = TemplateStats(
        totalTickets = totalTickets,
        soldTickets = soldTickets,
        availableTickets = availableTickets,
        closedSeats = closedSeats,
        totalRevenue = totalRevenue.toMoney(currency),
        presentUsers = 0,
        color = color
    )
}

private data class AttendanceAccumulator(
    var sold: Long = 0,
    var present: Long = 0,
    var presentOnTime: Long = 0,
    var presentLate: Long = 0,
    var absent: Long = 0
)
