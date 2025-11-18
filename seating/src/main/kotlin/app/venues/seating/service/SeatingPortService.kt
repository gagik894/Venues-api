package app.venues.seating.service

import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.*
import app.venues.seating.repository.*
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
    private val gaAreaRepository: GeneralAdmissionAreaRepository
) : SeatingApi {


    override fun getChartInfo(chartId: UUID): SeatingChartInfoDto? {
        val chart = seatingChartRepository.findById(chartId).orElse(null) ?: return null
        return SeatingChartInfoDto(
            chartId = chart.id,
            venueId = chart.venueId,
            chartName = chart.name,
            width = chart.width,
            height = chart.height
        )
    }

    override fun chartExists(chartId: UUID): Boolean {
        return seatingChartRepository.existsById(chartId)
    }

    override fun getSeatingChartName(chartId: UUID): String {
        return seatingChartRepository.findById(chartId)
            .map { it.name }
            .orElse("")
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
                boundaryPath = g.boundaryPath,
                displayColor = g.displayColor
            )
        }

        return SeatingChartStructureDto(
            chartId = chart.id,
            chartName = chart.name,
            width = chart.width,
            height = chart.height,
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

    override fun getSeatInfoByCode(code: String): SeatInfoDto? {
        val seat = chartSeatRepository.findByCode(code) ?: return null
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
            zoneName = table.zone.name
        )
    }

    override fun getGaInfo(gaId: Long): GaInfoDto? {
        val ga = gaAreaRepository.findById(gaId).orElse(null) ?: return null
        return GaInfoDto(
            id = ga.id ?: error("GA Area ID cannot be null"),
            code = ga.code,
            name = ga.name,
            capacity = ga.capacity,
            zoneId = ga.zone.id ?: error("Zone ID cannot be null")
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
            zoneName = table.zone.name
        )
    }
}

