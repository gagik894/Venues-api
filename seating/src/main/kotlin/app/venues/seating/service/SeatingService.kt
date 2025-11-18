package app.venues.seating.service

import app.venues.common.exception.VenuesException
import app.venues.seating.domain.*
import app.venues.seating.mapper.SeatingResponseMapper
import app.venues.seating.model.*
import app.venues.seating.repository.*
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Internal seating service for CRUD operations.
 * Manages seating charts, zones, seats, tables, and GA areas.
 * Used by REST controllers for venue management operations.
 * Follows SRP: separate from Port implementation.
 */
@Service
@Transactional
class SeatingService(
    private val seatingChartRepository: SeatingChartRepository,
    private val chartZoneRepository: ChartZoneRepository,
    private val chartSeatRepository: ChartSeatRepository,
    private val chartTableRepository: ChartTableRepository,
    private val gaAreaRepository: GeneralAdmissionAreaRepository,
    private val mapper: SeatingResponseMapper,
    private val venueApi: VenueApi
) {

    private val logger = KotlinLogging.logger {}

    // =================================================================================
    // CHART CRUD OPERATIONS
    // =================================================================================

    /**
     * Create new seating chart for venue.
     * @throws VenuesException.ResourceNotFound if venue not found
     * @throws VenuesException.ResourceConflict if chart name already exists
     */
    fun createSeatingChart(venueId: UUID, request: SeatingChartRequest): SeatingChartResponse {
        if (!venueApi.venueExists(venueId)) {
            throw VenuesException.ResourceNotFound("Venue not found")
        }

        if (seatingChartRepository.existsByVenueIdAndName(venueId, request.name)) {
            throw VenuesException.ResourceConflict("Chart name already exists")
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

        logger.info { "Created seating chart: ${saved.id} for venue: $venueId" }
        return mapper.toResponse(saved, venueName, 0, 0, 0)
    }

    /**
     * Update existing seating chart.
     * @throws VenuesException.ResourceNotFound if chart not found
     * @throws VenuesException.AuthorizationFailure if chart doesn't belong to venue
     * @throws VenuesException.ResourceConflict if new name conflicts
     */
    fun updateSeatingChart(chartId: UUID, venueId: UUID, request: SeatingChartRequest): SeatingChartResponse {
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found") }

        if (chart.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Chart does not belong to venue")
        }

        if (chart.name != request.name) {
            if (seatingChartRepository.existsByVenueIdAndName(venueId, request.name)) {
                throw VenuesException.ResourceConflict("Chart name already exists")
            }
        }

        chart.name = request.name
        chart.resizeCanvas(request.width, request.height)
        chart.backgroundUrl = request.backgroundUrl

        val saved = seatingChartRepository.save(chart)
        val stats = calculateChartStats(chartId)

        logger.info { "Updated seating chart: $chartId" }
        return mapper.toResponse(
            saved,
            venueApi.getVenueName(venueId),
            stats.seatCount,
            stats.gaCapacity,
            stats.zoneCount
        )
    }

    /**
     * Delete seating chart and all associated components.
     * @throws VenuesException.ResourceNotFound if chart not found
     * @throws VenuesException.AuthorizationFailure if chart doesn't belong to venue
     */
    fun deleteSeatingChart(chartId: UUID, venueId: UUID) {
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found") }

        if (chart.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Chart does not belong to venue")
        }

        seatingChartRepository.delete(chart)
        logger.info { "Deleted seating chart: $chartId" }
    }

    /**
     * Get paginated list of charts for venue.
     */
    @Transactional(readOnly = true)
    fun getSeatingChartsByVenue(venueId: UUID, pageable: Pageable): Page<SeatingChartResponse> {
        val venueName = venueApi.getVenueName(venueId)

        return seatingChartRepository.findByVenueId(venueId, pageable).map { chart ->
            val stats = calculateChartStats(chart.id)
            mapper.toResponse(chart, venueName, stats.seatCount, stats.gaCapacity, stats.zoneCount)
        }
    }

    /**
     * Get detailed chart with full hierarchy.
     * @throws VenuesException.ResourceNotFound if chart not found
     */
    @Transactional(readOnly = true)
    fun getSeatingChartDetailed(id: UUID): SeatingChartDetailedResponse {
        val chart = seatingChartRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found") }

        val allZones = chartZoneRepository.findByChartId(id)
        val venueName = venueApi.getVenueName(chart.venueId)

        return mapper.toDetailedResponse(chart, venueName, allZones)
    }

    // =================================================================================
    // ZONE MANAGEMENT
    // =================================================================================

    /**
     * Add zone (section/floor) to chart.
     * @throws VenuesException.ResourceNotFound if chart or parent zone not found
     * @throws VenuesException.ResourceConflict if code already exists or parent belongs to different chart
     */
    fun addZone(chartId: UUID, request: ZoneRequest): ZoneResponse {
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found") }

        val parentZone = request.parentZoneId?.let {
            chartZoneRepository.findById(it)
                .orElseThrow { VenuesException.ResourceNotFound("Parent zone not found") }
        }

        if (parentZone != null && parentZone.chart.id != chartId) {
            throw VenuesException.ResourceConflict("Parent zone belongs to different chart")
        }

        if (chartZoneRepository.existsByChartIdAndCode(chartId, request.code)) {
            throw VenuesException.ResourceConflict("Zone code already exists")
        }

        val zone = ChartZone(
            chart = chart,
            parentZone = parentZone,
            name = request.name,
            code = request.code,
            x = request.x,
            y = request.y,
            rotation = request.rotation,
            boundaryPath = request.boundaryPath,
            displayColor = request.displayColor
        )

        if (parentZone != null) {
            parentZone.addChildZone(zone)
        } else {
            chart.addZone(zone)
        }

        val saved = chartZoneRepository.save(zone)
        logger.info { "Created zone: ${saved.code} in chart: $chartId" }

        return mapper.toZoneResponse(saved)
    }

    // =================================================================================
    // TABLE MANAGEMENT
    // =================================================================================

    /**
     * Add physical table to zone.
     * @throws VenuesException.ResourceNotFound if zone not found
     * @throws VenuesException.ResourceConflict if zone belongs to different chart
     */
    fun addTable(chartId: UUID, request: TableRequest): TableResponse {
        val zone = findZoneAndVerifyChart(request.zoneId, chartId)

        val table = ChartTable(
            zone = zone,
            tableNumber = request.tableNumber,
            code = request.code,
            seatCapacity = request.seatCapacity,
            shape = TableShape.valueOf(request.shape.uppercase()),
            x = request.x,
            y = request.y,
            width = request.width,
            height = request.height,
            rotation = request.rotation
        )

        val saved = chartTableRepository.save(table)
        logger.info { "Created table: ${saved.code} in zone: ${zone.code}" }

        return mapper.toTableResponse(saved)
    }

    // =================================================================================
    // GA AREA MANAGEMENT
    // =================================================================================

    /**
     * Add general admission area to zone.
     * @throws VenuesException.ResourceNotFound if zone not found
     * @throws VenuesException.ResourceConflict if zone belongs to different chart
     */
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
        logger.info { "Created GA area: ${saved.code} with capacity ${saved.capacity}" }

        return mapper.toGaAreaResponse(saved)
    }

    // =================================================================================
    // SEAT MANAGEMENT
    // =================================================================================

    /**
     * Add single seat to zone.
     * @throws VenuesException.ResourceNotFound if zone or table not found
     * @throws VenuesException.ResourceConflict if table/zone mismatch or duplicate code
     */
    fun addSeat(chartId: UUID, request: SeatRequest): SeatResponse {
        val zone = findZoneAndVerifyChart(request.zoneId, chartId)

        val table = request.tableId?.let {
            chartTableRepository.findById(it)
                .orElseThrow { VenuesException.ResourceNotFound("Table not found") }
        }

        if (table != null && table.zone.id != zone.id) {
            throw VenuesException.ResourceConflict("Table must belong to same zone as seat")
        }

        val seat = ChartSeat(
            zone = zone,
            row = request.rowLabel,
            number = request.seatNumber,
            x = request.x,
            y = request.y,
            category = request.categoryKey
        )
        seat.rotation = request.rotation
        seat.isAccessible = request.isAccessible
        seat.isObstructedView = request.isObstructed

        if (table != null) {
            table.attachSeat(seat)
        }

        val zoneId = zone.id ?: error("Zone ID cannot be null")
        if (chartSeatRepository.existsByZoneIdAndCode(zoneId, seat.code)) {
            throw VenuesException.ResourceConflict("Seat code already exists in zone")
        }

        val saved = chartSeatRepository.save(seat)
        logger.info { "Created seat: ${saved.code}" }

        return mapper.toSeatResponse(saved)
    }

    /**
     * Add multiple seats in batch.
     * @throws VenuesException.ResourceNotFound if zone not found
     * @throws VenuesException.ValidationFailure if duplicate codes in batch
     */
    fun addSeatsBatch(chartId: UUID, request: BatchSeatRequest): List<SeatResponse> {
        val zone = findZoneAndVerifyChart(request.zoneId, chartId)

        val seats = request.seats.map { item ->
            val seat = ChartSeat(
                zone = zone,
                row = item.rowLabel,
                number = item.seatNumber,
                x = item.x,
                y = item.y,
                category = item.categoryKey
            )
            seat.rotation = item.rotation
            seat
        }

        if (seats.map { it.code }.distinct().size != seats.size) {
            throw VenuesException.ValidationFailure("Duplicate seat codes in batch")
        }

        val saved = chartSeatRepository.saveAll(seats)
        logger.info { "Created ${saved.size} seats in zone: ${zone.code}" }

        return saved.map { mapper.toSeatResponse(it) }
    }

    /**
     * Delete seat from chart.
     * @throws VenuesException.ResourceNotFound if seat not found
     */
    fun deleteSeat(seatId: Long) {
        if (!chartSeatRepository.existsById(seatId)) {
            throw VenuesException.ResourceNotFound("Seat not found")
        }

        chartSeatRepository.deleteById(seatId)
        logger.info { "Deleted seat: $seatId" }
    }

    // =================================================================================
    // PRIVATE HELPERS
    // =================================================================================

    private fun findZoneAndVerifyChart(zoneId: Long, chartId: UUID): ChartZone {
        val zone = chartZoneRepository.findById(zoneId)
            .orElseThrow { VenuesException.ResourceNotFound("Zone not found") }

        if (zone.chart.id != chartId) {
            throw VenuesException.ResourceConflict("Zone belongs to different chart")
        }

        return zone
    }

    private data class ChartStats(val seatCount: Int, val gaCapacity: Int, val zoneCount: Int)

    private fun calculateChartStats(chartId: UUID): ChartStats {
        val seatCount = chartSeatRepository.countByZoneChartId(chartId).toInt()
        val zoneCount = chartZoneRepository.countByChartId(chartId).toInt()
        val gaCapacity = gaAreaRepository.sumCapacityByChartId(chartId)?.toInt() ?: 0

        return ChartStats(seatCount, gaCapacity, zoneCount)
    }
}

