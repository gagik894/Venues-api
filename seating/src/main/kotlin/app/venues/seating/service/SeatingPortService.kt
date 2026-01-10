package app.venues.seating.service

import app.venues.common.exception.VenuesException
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.*
import app.venues.seating.domain.BackgroundTransformMapper
import app.venues.seating.repository.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Port implementation for seating module.
 * Provides stable API for cross-module communication.
 * Follows SRP: only implements SeatingApi port contract.
 */
@Service
@Transactional(readOnly = true)
class SeatingPortService(
    private val seatingChartRepository: SeatingChartRepository,
    private val chartZoneRepository: ChartZoneRepository,
    private val chartSeatRepository: ChartSeatRepository,
    private val chartTableRepository: ChartTableRepository,
    private val gaAreaRepository: GeneralAdmissionAreaRepository,
    private val messageSource: MessageSource
) : SeatingApi {

    private val logger = KotlinLogging.logger {}


    override fun getChartInfo(chartId: UUID): SeatingChartInfoDto? {
        val chart = seatingChartRepository.findById(chartId).orElse(null) ?: return null
        return SeatingChartInfoDto(
            chartId = chart.id,
            venueId = chart.venueId,
            chartName = chart.name,
            width = chart.width,
            height = chart.height,
            backgroundUrl = chart.backgroundUrl,
            backgroundTransform = BackgroundTransformMapper.fromJson(chart.backgroundTransformJson)?.toDto()
        )
    }

    override fun chartExists(chartId: UUID): Boolean {
        return seatingChartRepository.existsById(chartId)
    }

    override fun getSeatingChartName(chartId: UUID): String {
        return seatingChartRepository.findById(chartId)
            .map { it.name }
            .orElseThrow {
                VenuesException.ResourceNotFound(
                    message = "Seating chart not found",
                    errorCode = "SEATING_CHART_NOT_FOUND"
                )
            }
    }

    override fun getSeatCount(chartId: UUID): Int {
        return chartSeatRepository.countByZoneChartId(chartId).toInt()
    }

    override fun getChartStructure(chartId: UUID): SeatingChartStructureDto? {
        val chart = seatingChartRepository.findById(chartId).orElse(null) ?: return null

        val zones = chartZoneRepository.findByChartId(chartId)
        val seats = chartSeatRepository.findByChartId(chartId)
        val tables = chartTableRepository.findByChartId(chartId)
        val gaAreas = gaAreaRepository.findByChartId(chartId)

        val zoneDtos = zones.map { z ->
            ZoneDto(
                id = z.id ?: error("Zone ID cannot be null"),
                name = z.name,
                code = z.code,
                parentZoneId = z.parentZone?.id,
                x = z.x,
                y = z.y,
                rotation = z.rotation,
                boundaryPath = z.boundaryPath,
                displayColor = z.displayColor
            )
        }

        val tableDtos = tables.map { t ->
            TableDto(
                id = t.id ?: error("Table ID cannot be null"),
                zoneId = t.zone.id ?: error("Zone ID cannot be null"),
                tableNumber = t.tableNumber,
                code = t.code,
                shape = t.shape.name,
                seatCapacity = t.seatCapacity,
                categoryKey = t.categoryKey,
                x = t.x,
                y = t.y,
                width = t.width,
                height = t.height,
                rotation = t.rotation
            )
        }

        val seatDtos = seats.map { s ->
            SeatDto(
                id = s.id ?: error("Seat ID cannot be null"),
                zoneId = s.zone.id ?: error("Zone ID cannot be null"),
                tableId = s.table?.id,
                code = s.code,
                rowLabel = s.rowLabel,
                seatNumber = s.seatNumber,
                categoryKey = s.categoryKey,
                isAccessible = s.isAccessible,
                isObstructed = s.isObstructedView,
                x = s.x,
                y = s.y,
                rotation = s.rotation
            )
        }

        val gaDtos = gaAreas.map { g ->
            GaAreaDto(
                id = g.id ?: error("GA Area ID cannot be null"),
                zoneId = g.zone.id ?: error("Zone ID cannot be null"),
                name = g.name,
                code = g.code,
                capacity = g.capacity,
                categoryKey = g.categoryKey,
                boundaryPath = g.boundaryPath,
                displayColor = g.displayColor
            )
        }

        return SeatingChartStructureDto(
            chartId = chart.id,
            chartName = chart.name,
            width = chart.width,
            height = chart.height,
            backgroundUrl = chart.backgroundUrl,
            backgroundTransform = BackgroundTransformMapper.fromJson(chart.backgroundTransformJson)?.toDto(),
            zones = zoneDtos,
            tables = tableDtos,
            seats = seatDtos,
            gaAreas = gaDtos
        )
    }

    override fun getSeatInfo(seatId: Long): SeatInfoDto? {
        val seat = chartSeatRepository.findById(seatId).orElse(null) ?: return null
        return SeatInfoDto(
            id = seat.id ?: error("Seat ID cannot be null"),
            code = seat.code,
            seatNumber = seat.seatNumber,
            rowLabel = seat.rowLabel,
            zoneId = seat.zone.id ?: error("Zone ID cannot be null"),
            zoneName = seat.zone.name,
            categoryKey = seat.categoryKey
        )
    }

    override fun getSeatInfoByCode(chartId: UUID, code: String): SeatInfoDto? {
        val seat = chartSeatRepository.findByChartIdAndCode(chartId, code) ?: return null
        return SeatInfoDto(
            id = seat.id ?: error("Seat ID cannot be null"),
            code = seat.code,
            seatNumber = seat.seatNumber,
            rowLabel = seat.rowLabel,
            zoneId = seat.zone.id ?: error("Zone ID cannot be null"),
            zoneName = seat.zone.name,
            categoryKey = seat.categoryKey
        )
    }

    override fun getSeatInfoBatch(seatIds: List<Long>): List<SeatInfoDto> {
        if (seatIds.isEmpty()) return emptyList()

        return chartSeatRepository.findAllById(seatIds).map { seat ->
            SeatInfoDto(
                id = seat.id ?: error("Seat ID cannot be null"),
                code = seat.code,
                seatNumber = seat.seatNumber,
                rowLabel = seat.rowLabel,
                zoneId = seat.zone.id ?: error("Zone ID cannot be null"),
                zoneName = seat.zone.name,
                categoryKey = seat.categoryKey
            )
        }
    }

    override fun seatExists(seatId: Long): Boolean {
        return chartSeatRepository.existsById(seatId)
    }

    override fun getSectionInfo(sectionId: Long): SectionInfoDto? {
        val zone = chartZoneRepository.findById(sectionId).orElse(null) ?: return null
        return SectionInfoDto(
            id = zone.id ?: error("Zone ID cannot be null"),
            code = zone.code,
            name = zone.name
        )
    }

    override fun getTableInfo(tableId: Long): TableInfoDto? {
        val table = chartTableRepository.findById(tableId).orElse(null) ?: return null
        return TableInfoDto(
            id = table.id ?: error("Table ID cannot be null"),
            code = table.code,
            tableNumber = table.tableNumber,
            seatCapacity = table.seatCapacity,
            zoneId = table.zone.id ?: error("Zone ID cannot be null"),
            zoneName = table.zone.name,
            categoryKey = table.categoryKey
        )
    }

    override fun getGaInfo(gaId: Long): GaInfoDto? {
        val ga = gaAreaRepository.findById(gaId).orElse(null) ?: return null
        return GaInfoDto(
            id = ga.id ?: error("GA Area ID cannot be null"),
            code = ga.code,
            name = ga.name,
            capacity = ga.capacity,
            zoneId = ga.zone.id ?: error("Zone ID cannot be null"),
            categoryKey = ga.categoryKey
        )
    }

    override fun getGaInfoByCode(chartId: UUID, code: String): GaInfoDto? {
        val ga = gaAreaRepository.findByChartIdAndCode(chartId, code) ?: return null
        return GaInfoDto(
            id = ga.id ?: error("GA Area ID cannot be null"),
            code = ga.code,
            name = ga.name,
            capacity = ga.capacity,
            zoneId = ga.zone.id ?: error("Zone ID cannot be null"),
            categoryKey = ga.categoryKey
        )
    }

    override fun getTableForSeat(seatId: Long): TableInfoDto? {
        val seat = chartSeatRepository.findById(seatId).orElse(null) ?: return null
        val table = seat.table ?: return null

        return TableInfoDto(
            id = table.id ?: error("Table ID cannot be null"),
            code = table.code,
            tableNumber = table.tableNumber,
            seatCapacity = table.seatCapacity,
            zoneId = table.zone.id ?: error("Zone ID cannot be null"),
            zoneName = table.zone.name,
            categoryKey = table.categoryKey
        )
    }

    override fun getTableInfoByCode(chartId: UUID, code: String): TableInfoDto? {
        val table = chartTableRepository.findByChartIdAndCode(chartId, code) ?: return null
        return TableInfoDto(
            id = table.id ?: error("Table ID cannot be null"),
            code = table.code,
            tableNumber = table.tableNumber,
            seatCapacity = table.seatCapacity,
            zoneId = table.zone.id ?: error("Zone ID cannot be null"),
            zoneName = table.zone.name,
            categoryKey = table.categoryKey
        )
    }

    override fun getSeatsForTable(tableId: Long): List<SeatInfoDto> {
        val seats = chartSeatRepository.findByTableId(tableId)
        return seats.map { seat ->
            SeatInfoDto(
                id = seat.id ?: error("Seat ID cannot be null"),
                code = seat.code,
                seatNumber = seat.seatNumber,
                rowLabel = seat.rowLabel,
                zoneId = seat.zone.id ?: error("Zone ID cannot be null"),
                zoneName = seat.zone.name,
                categoryKey = seat.categoryKey
            )
        }
    }

    @org.springframework.cache.annotation.Cacheable("zoneHierarchy")
    override fun getZoneHierarchy(zoneId: Long): List<SectionInfoDto> {
        val hierarchy = mutableListOf<SectionInfoDto>()
        var currentZone = chartZoneRepository.findById(zoneId).orElse(null)

        while (currentZone != null) {
            hierarchy.add(
                0, // Add to beginning to maintain Root -> Leaf order
                SectionInfoDto(
                    id = currentZone.id ?: error("Zone ID cannot be null"),
                    code = currentZone.code,
                    name = currentZone.name
                )
            )
            currentZone = currentZone.parentZone
        }

        return hierarchy
    }

    @org.springframework.cache.annotation.Cacheable("seatLocationLines")
    override fun getSeatLocationLines(seatId: Long, locale: String?): List<String> {
        return try {
            val seatInfo = getSeatInfo(seatId) ?: return emptyList()
            val hierarchy = getZoneHierarchy(seatInfo.zoneId)
            val resolvedLocale = resolveLocale(locale)

            val lines = mutableListOf<String>()
            // Add zone hierarchy (from root to leaf)
            hierarchy.forEach { zone -> lines.add(zone.name) }
            // Add row and seat with i18n
            if (seatInfo.rowLabel.isNotBlank()) {
                val rowLabel = getMessage("ticket.location.row", resolvedLocale)
                lines.add("$rowLabel ${seatInfo.rowLabel}")
            }
            val seatLabel = getMessage("ticket.location.seat", resolvedLocale)
            lines.add("$seatLabel ${seatInfo.seatNumber}")

            lines
        } catch (e: Exception) {
            logger.debug { "Could not resolve seat location for $seatId: ${e.message}" }
            emptyList()
        }
    }

    @org.springframework.cache.annotation.Cacheable("gaLocationLines")
    override fun getGaLocationLines(gaAreaId: Long, locale: String?): List<String> {
        return try {
            val gaInfo = getGaInfo(gaAreaId) ?: return emptyList()
            val hierarchy = getZoneHierarchy(gaInfo.zoneId)

            val lines = mutableListOf<String>()
            hierarchy.forEach { zone -> lines.add(zone.name) }
            // Add GA area name if different from last zone
            if (lines.isEmpty() || lines.last() != gaInfo.name) {
                lines.add(gaInfo.name)
            }

            lines
        } catch (e: Exception) {
            logger.debug { "Could not resolve GA location for $gaAreaId: ${e.message}" }
            emptyList()
        }
    }

    @org.springframework.cache.annotation.Cacheable("tableLocationLines")
    override fun getTableLocationLines(tableId: Long, locale: String?): List<String> {
        return try {
            val tableInfo = getTableInfo(tableId) ?: return emptyList()
            val hierarchy = getZoneHierarchy(tableInfo.zoneId)
            val resolvedLocale = resolveLocale(locale)

            val lines = mutableListOf<String>()
            hierarchy.forEach { zone -> lines.add(zone.name) }
            val tableLabel = getMessage("ticket.location.table", resolvedLocale)
            lines.add("$tableLabel ${tableInfo.tableNumber}")

            lines
        } catch (e: Exception) {
            logger.debug { "Could not resolve table location for $tableId: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Resolve Locale from language code string.
     */
    private fun resolveLocale(localeCode: String?): Locale {
        return when (localeCode?.lowercase()) {
            "hy" -> Locale("hy")
            "ru" -> Locale("ru")
            else -> Locale.ENGLISH
        }
    }

    /**
     * Get i18n message with fallback to key.
     */
    private fun getMessage(key: String, locale: Locale): String {
        return try {
            messageSource.getMessage(key, null, key, locale) ?: key
        } catch (e: Exception) {
            key
        }
    }

    private fun app.venues.seating.model.BackgroundTransform.toDto(): BackgroundTransformDto =
        BackgroundTransformDto(x = x, y = y, scale = scale, opacity = opacity)
}