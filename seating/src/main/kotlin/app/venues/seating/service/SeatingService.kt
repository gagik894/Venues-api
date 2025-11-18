package app.venues.seating.service

import app.venues.common.exception.VenuesException
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.*
import app.venues.seating.api.mapper.SeatingMapper
import app.venues.seating.domain.*
import app.venues.seating.repository.*
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class SeatingService(
    private val seatingChartRepository: SeatingChartRepository,
    private val chartZoneRepository: ChartZoneRepository,
    private val chartSeatRepository: ChartSeatRepository,
    private val chartTableRepository: ChartTableRepository,
    private val gaAreaRepository: GeneralAdmissionAreaRepository,
    private val seatingMapper: SeatingMapper,
    private val venueApi: VenueApi
) : SeatingApi {

    private val logger = KotlinLogging.logger {}

    // =================================================================================
    // PUBLIC API IMPLEMENTATION (SeatingApi Port)
    // =================================================================================

    @Transactional(readOnly = true)
    override fun getSeatInfo(seatId: Long): SeatInfoDto? {
        return chartSeatRepository.findById(seatId)
            .map(seatingMapper::toSeatInfoDto)
            .orElse(null)
    }

    @Transactional(readOnly = true)
    override fun getSeatInfoBatch(seatIds: List<Long>): List<SeatInfoDto> {
        if (seatIds.isEmpty()) return emptyList()
        return chartSeatRepository.findAllById(seatIds)
            .map(seatingMapper::toSeatInfoDto)
    }

    @Transactional(readOnly = true)
    override fun getSeatInfoByCode(code: String): SeatInfoDto? {
        val seat = chartSeatRepository.findByCode(code) ?: return null
        return seatingMapper.toSeatInfoDto(seat)
    }

    @Transactional(readOnly = true)
    override fun seatExists(seatId: Long): Boolean = chartSeatRepository.existsById(seatId)

    @Transactional(readOnly = true)
    override fun getSectionInfo(sectionId: Long): SectionInfoDto? {
        return chartZoneRepository.findById(sectionId)
            .map(seatingMapper::toSectionInfoDto)
            .orElse(null)
    }

    @Transactional(readOnly = true)
    override fun getTableInfo(tableId: Long): TableInfoDto? {
        return chartTableRepository.findById(tableId)
            .map(seatingMapper::toTableInfoDto)
            .orElse(null)
    }

    @Transactional(readOnly = true)
    override fun getGaInfo(gaId: Long): GaInfoDto? {
        return gaAreaRepository.findById(gaId)
            .map(seatingMapper::toGaInfoDto)
            .orElse(null)
    }

    @Transactional(readOnly = true)
    override fun getTableForSeat(seatId: Long): TableInfoDto? {
        val seat = chartSeatRepository.findById(seatId).orElse(null) ?: return null
        return seat.table?.let { seatingMapper.toTableInfoDto(it) }
    }

    @Transactional(readOnly = true)
    override fun getSeatingChartName(chartId: UUID): String {
        return seatingChartRepository.findById(chartId).map { it.name }.orElse("")
    }

    @Transactional(readOnly = true)
    override fun getChartStructure(chartId: UUID): SeatingChartStructureDto? {
        val chart = seatingChartRepository.findById(chartId).orElse(null) ?: return null

        val zones = chartZoneRepository.findByChartId(chartId)
        val seats = chartSeatRepository.findByChartId(chartId)
        val tables = chartTableRepository.findByChartId(chartId)
        val gaAreas = gaAreaRepository.findByChartId(chartId)

        // Map Zones
        val zoneDtos = zones.map { z ->
            ZoneDto(
                id = z.id!!,
                name = z.name,
                code = z.code,
                parentZoneId = z.parentZone?.id,
                x = z.x, y = z.y, rotation = z.rotation,
                boundaryPath = z.boundaryPath, displayColor = z.displayColor
            )
        }

        // Map Tables
        val tableDtos = tables.map { t ->
            TableDto(
                id = t.id!!,
                zoneId = t.zone.id!!,
                tableNumber = t.tableNumber,
                code = t.code,
                shape = t.shape.name,
                seatCapacity = t.seatCapacity,
                x = t.x, y = t.y, width = t.width, height = t.height, rotation = t.rotation
            )
        }

        // Map Seats
        val seatDtos = seats.map { s ->
            SeatDto(
                id = s.id!!,
                zoneId = s.zone.id!!,
                tableId = s.table?.id,
                code = s.code,
                rowLabel = s.rowLabel,
                seatNumber = s.seatNumber,
                categoryKey = s.categoryKey,
                isAccessible = s.isAccessible,
                isObstructed = s.isObstructedView,
                x = s.x, y = s.y, rotation = s.rotation
            )
        }

        // Map GA
        val gaDtos = gaAreas.map { g ->
            GaAreaDto(
                id = g.id!!,
                zoneId = g.zone.id!!,
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

    // =================================================================================
    // CHART CRUD OPERATIONS (For Venue Controller)
    // =================================================================================

    fun createSeatingChart(venueId: UUID, request: SeatingChartRequest): SeatingChartResponse {
        if (!venueApi.venueExists(venueId)) {
            throw VenuesException.ResourceNotFound("Venue not found: $venueId")
        }
        if (seatingChartRepository.existsByVenueIdAndName(venueId, request.name)) {
            throw VenuesException.ResourceConflict("Chart name '${request.name}' already exists")
        }

        val chart = SeatingChart(
            venueId = venueId,
            name = request.name,
            width = request.width,
            height = request.height,
            backgroundUrl = request.backgroundUrl
        )

        val saved = seatingChartRepository.save(chart)
        val venueName = venueApi.getVenueName(venueId)

        return seatingMapper.toResponse(saved, venueName, 0, 0, 0)
    }

    fun updateSeatingChart(chartId: UUID, venueId: UUID, request: SeatingChartRequest): SeatingChartResponse {
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found: $chartId") }

        if (chart.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Chart does not belong to venue")
        }

        if (chart.name != request.name) {
            if (seatingChartRepository.existsByVenueIdAndName(venueId, request.name)) {
                throw VenuesException.ResourceConflict("Chart name '${request.name}' already exists")
            }
        }

        chart.name = request.name
        chart.resizeCanvas(request.width, request.height)
        chart.backgroundUrl = request.backgroundUrl

        val saved = seatingChartRepository.save(chart)

        // Recalculate stats
        val seatCount = chartSeatRepository.countByZoneChartId(chartId).toInt()
        val zoneCount = chartZoneRepository.countByChartId(chartId).toInt()
        val gaCapacity = gaAreaRepository.sumCapacityByChartId(chartId)?.toInt() ?: 0

        return seatingMapper.toResponse(
            saved,
            venueApi.getVenueName(venueId),
            seatCount,
            gaCapacity,
            zoneCount
        )
    }

    fun deleteSeatingChart(chartId: UUID, venueId: UUID) {
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found: $chartId") }

        if (chart.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Chart does not belong to venue")
        }
        seatingChartRepository.delete(chart)
    }

    @Transactional(readOnly = true)
    fun getSeatingChartsByVenue(venueId: UUID, pageable: Pageable): Page<SeatingChartResponse> {
        val venueName = venueApi.getVenueName(venueId)
        return seatingChartRepository.findByVenueId(venueId, pageable).map { chart ->
            val seatCount = chartSeatRepository.countByZoneChartId(chart.id).toInt()
            val zoneCount = chartZoneRepository.countByChartId(chart.id).toInt()
            val gaCapacity = gaAreaRepository.sumCapacityByChartId(chart.id)?.toInt() ?: 0
            seatingMapper.toResponse(chart, venueName, seatCount, gaCapacity, zoneCount)
        }
    }

    @Transactional(readOnly = true)
    fun getSeatingChartDetailed(id: UUID): SeatingChartDetailedResponse {
        val chart = seatingChartRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found: $id") }

        val allZones = chartZoneRepository.findByChartId(id)
        val venueName = venueApi.getVenueName(chart.venueId)

        return seatingMapper.toDetailedResponse(chart, venueName, allZones)
    }

    // =================================================================================
    // COMPONENT MANAGEMENT
    // =================================================================================

    fun addZone(chartId: UUID, request: ZoneRequest): ZoneResponse {
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found") }

        val parentZone = request.parentZoneId?.let {
            chartZoneRepository.findById(it)
                .orElseThrow { VenuesException.ResourceNotFound("Parent zone not found: $it") }
        }

        if (parentZone != null && parentZone.chart.id != chartId) {
            throw VenuesException.ResourceConflict("Parent zone belongs to different chart")
        }

        if (chartZoneRepository.existsByChartIdAndCode(chartId, request.code)) {
            throw VenuesException.ResourceConflict("Zone code '${request.code}' already exists")
        }

        val zone = ChartZone(
            chart = chart,
            parentZone = parentZone,
            name = request.name,
            code = request.code,
            x = request.x, y = request.y, rotation = request.rotation,
            boundaryPath = request.boundaryPath, displayColor = request.displayColor
        )

        // Domain logic
        if (parentZone != null) parentZone.addChildZone(zone) else chart.addZone(zone)

        val saved = chartZoneRepository.save(zone)
        return seatingMapper.toZoneResponse(saved)
    }

    fun addTable(chartId: UUID, request: TableRequest): TableResponse {
        val zone = findZoneAndVerifyChart(request.zoneId, chartId)

        val table = ChartTable(
            zone = zone,
            tableNumber = request.tableNumber,
            code = request.code,
            seatCapacity = request.seatCapacity,
            shape = TableShape.valueOf(request.shape.uppercase()),
            x = request.x, y = request.y, width = request.width, height = request.height, rotation = request.rotation
        )

        val saved = chartTableRepository.save(table)
        return seatingMapper.toTableResponse(saved)
    }

    fun addGaArea(chartId: UUID, request: GaAreaRequest): GaAreaResponse {
        val zone = findZoneAndVerifyChart(request.zoneId, chartId)

        val ga = GeneralAdmissionArea(
            zone = zone,
            name = request.name,
            code = request.code,
            capacity = request.capacity,
            boundaryPath = request.boundaryPath,
            displayColor = request.displayColor
        )

        val saved = gaAreaRepository.save(ga)
        return seatingMapper.toGaAreaResponse(saved)
    }

    fun addSeat(chartId: UUID, request: SeatRequest): SeatResponse {
        val zone = findZoneAndVerifyChart(request.zoneId, chartId)

        val table = request.tableId?.let {
            chartTableRepository.findById(it).orElseThrow { VenuesException.ResourceNotFound("Table not found: $it") }
        }

        if (table != null && table.zone.id != zone.id) {
            throw VenuesException.ResourceConflict("Table must belong to same zone as seat")
        }

        val seat = ChartSeat(
            zone = zone,
            row = request.rowLabel,
            number = request.seatNumber,
            x = request.x, y = request.y, category = request.categoryKey
        )
        seat.rotation = request.rotation
        seat.isAccessible = request.isAccessible
        seat.isObstructedView = request.isObstructed

        if (table != null) table.attachSeat(seat)

        if (chartSeatRepository.existsByZoneIdAndCode(zone.id!!, seat.code)) {
            throw VenuesException.ResourceConflict("Seat code '${seat.code}' already exists in this zone")
        }

        val saved = chartSeatRepository.save(seat)
        return seatingMapper.toSeatResponse(saved)
    }

    fun addSeatsBatch(chartId: UUID, request: BatchSeatRequest): List<SeatResponse> {
        val zone = findZoneAndVerifyChart(request.zoneId, chartId)

        val seats = request.seats.map { item ->
            val s = ChartSeat(
                zone = zone,
                row = item.rowLabel,
                number = item.seatNumber,
                x = item.x, y = item.y, category = item.categoryKey
            )
            s.rotation = item.rotation
            s
        }

        // Code uniqueness check inside batch
        if (seats.map { it.code }.distinct().size != seats.size) {
            throw VenuesException.ValidationFailure("Duplicate seat codes in batch")
        }

        val saved = chartSeatRepository.saveAll(seats)
        return saved.map { seatingMapper.toSeatResponse(it) }
    }

    fun deleteSeat(seatId: Long) {
        if (!chartSeatRepository.existsById(seatId)) {
            throw VenuesException.ResourceNotFound("Seat not found")
        }
        chartSeatRepository.deleteById(seatId)
    }

    private fun findZoneAndVerifyChart(zoneId: Long, chartId: UUID): ChartZone {
        val zone = chartZoneRepository.findById(zoneId)
            .orElseThrow { VenuesException.ResourceNotFound("Zone not found: $zoneId") }
        if (zone.chart.id != chartId) {
            throw VenuesException.ResourceConflict("Zone belongs to a different chart")
        }
        return zone
    }
}