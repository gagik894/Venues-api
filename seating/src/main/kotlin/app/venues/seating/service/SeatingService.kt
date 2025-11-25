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
     * Create a seating chart together with its full layout in a single request.
     */
    fun createSeatingChartWithLayout(
        venueId: UUID,
        request: SeatingChartLayoutRequest
    ): SeatingChartDetailedResponse {
        if (!venueApi.venueExists(venueId)) {
            throw VenuesException.ResourceNotFound("Venue not found")
        }

        if (seatingChartRepository.existsByVenueIdAndName(venueId, request.chart.name)) {
            throw VenuesException.ResourceConflict("Chart name already exists")
        }

        val chart = SeatingChart(
            venueId = venueId,
            name = request.chart.name,
            width = request.chart.width,
            height = request.chart.height,
            backgroundUrl = request.chart.backgroundUrl
        )

        val saved = seatingChartRepository.save(chart)
        applyLayout(saved, request)

        logger.info { "Created seating chart ${saved.id} with full layout" }
        return getSeatingChartDetailed(saved.id ?: error("Chart ID cannot be null after save"))
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
     * Replace chart metadata and its entire component tree (zones, seats, tables, GA areas).
     */
    fun replaceSeatingChartLayout(
        chartId: UUID,
        venueId: UUID,
        request: SeatingChartLayoutRequest
    ): SeatingChartDetailedResponse {
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found") }

        if (chart.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Chart does not belong to venue")
        }

        if (chart.name != request.chart.name && seatingChartRepository.existsByVenueIdAndName(
                venueId,
                request.chart.name
            )
        ) {
            throw VenuesException.ResourceConflict("Chart name already exists")
        }

        chart.name = request.chart.name
        chart.resizeCanvas(request.chart.width, request.chart.height)
        chart.backgroundUrl = request.chart.backgroundUrl

        val existingZones = chartZoneRepository.findByChartId(chartId)
        if (existingZones.isNotEmpty()) {
            chartZoneRepository.deleteAll(existingZones)
            chartZoneRepository.flush()
        }

        applyLayout(chart, request)
        logger.info { "Replaced seating chart layout for $chartId" }

        return getSeatingChartDetailed(chartId)
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
     * Get overview list of charts for venue.
     */
    @Transactional(readOnly = true)
    fun getSeatingChartsOverviewByVenue(venueId: UUID): List<SeatingChartOverviewResponse> {
        return seatingChartRepository.findAllByVenueId(venueId).map { chart ->
            mapper.toOverviewResponse(chart)
        }
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

    private fun applyLayout(chart: SeatingChart, request: SeatingChartLayoutRequest) {
        if (request.zones.isEmpty()) {
            throw VenuesException.ValidationFailure("At least one zone is required")
        }

        ensureUnique(request.zones.map { it.code }, "zone codes")
        ensureUnique(request.tables.map { it.code }, "table codes")
        ensureUnique(request.gaAreas.map { it.code }, "GA area codes")

        val zoneDrafts = request.zones.associate { dto ->
            dto.code to ChartZone(
                chart = chart,
                parentZone = null,
                name = dto.name,
                code = dto.code,
                x = dto.x,
                y = dto.y,
                rotation = dto.rotation,
                boundaryPath = dto.boundaryPath,
                displayColor = dto.displayColor
            )
        }

        request.zones.forEach { dto ->
            val zone = zoneDrafts.getValue(dto.code)
            val parentCode = dto.parentCode
            if (parentCode != null) {
                val parent = zoneDrafts[parentCode]
                    ?: throw VenuesException.ValidationFailure("Parent zone '$parentCode' not found for zone '${dto.code}'")
                parent.addChildZone(zone)
            } else {
                chart.addZone(zone)
            }
        }

        val persistedZones = chartZoneRepository.saveAll(zoneDrafts.values)
        val zonesByCode = persistedZones.associateBy { it.code }

        val seatsPerTable = request.seats
            .mapNotNull { it.tableCode }
            .groupingBy { it }
            .eachCount()

        val tableEntities = request.tables.map { dto ->
            val zone = zonesByCode[dto.zoneCode]
                ?: throw VenuesException.ValidationFailure("Zone '${dto.zoneCode}' not found for table '${dto.code}'")
            val seatCount = seatsPerTable[dto.code] ?: 0
            val resolvedCapacity = resolveSeatCapacity(dto, seatCount)
            ChartTable(
                zone = zone,
                tableNumber = dto.tableNumber,
                code = dto.code,
                seatCapacity = resolvedCapacity,
                categoryKey = dto.categoryKey,
                shape = TableShape.valueOf(dto.shape.uppercase()),
                x = dto.x,
                y = dto.y,
                width = dto.width,
                height = dto.height,
                rotation = dto.rotation
            )
        }
        val tablesByCode = if (tableEntities.isNotEmpty()) {
            chartTableRepository.saveAll(tableEntities).associateBy { it.code }
        } else {
            emptyMap()
        }

        val gaEntities = request.gaAreas.map { dto ->
            val zone = zonesByCode[dto.zoneCode]
                ?: throw VenuesException.ValidationFailure("Zone '${dto.zoneCode}' not found for GA area '${dto.code}'")
            GeneralAdmissionArea(
                zone = zone,
                name = dto.name,
                code = dto.code,
                capacity = dto.capacity,
                categoryKey = dto.categoryKey,
                boundaryPath = dto.boundaryPath,
                displayColor = dto.displayColor
            )
        }
        if (gaEntities.isNotEmpty()) {
            gaAreaRepository.saveAll(gaEntities)
        }

        ensureUnique(
            request.seats.map { "${it.zoneCode}:${it.rowLabel}:${it.seatNumber}" },
            "seat definitions"
        )

        val seatEntities = request.seats.map { dto ->
            val zone = zonesByCode[dto.zoneCode]
                ?: throw VenuesException.ValidationFailure("Zone '${dto.zoneCode}' not found for seat ${dto.rowLabel}-${dto.seatNumber}")
            val seat = ChartSeat(
                zone = zone,
                row = dto.rowLabel,
                number = dto.seatNumber,
                x = dto.x,
                y = dto.y,
                category = dto.categoryKey
            )
            seat.rotation = dto.rotation
            seat.isAccessible = dto.isAccessible
            seat.isObstructedView = dto.isObstructed

            val tableCode = dto.tableCode
            if (tableCode != null) {
                val table = tablesByCode[tableCode]
                    ?: throw VenuesException.ValidationFailure("Table '$tableCode' not found for seat ${dto.rowLabel}-${dto.seatNumber}")
                table.attachSeat(seat)
            }

            seat
        }
        if (seatEntities.isNotEmpty()) {
            chartSeatRepository.saveAll(seatEntities)
        }
    }

    private fun resolveSeatCapacity(dto: TableLayoutRequest, seatCount: Int): Int {
        val requested = dto.seatCapacity

        if (seatCount > 0) {
            if (requested != null && requested != seatCount) {
                throw VenuesException.ValidationFailure(
                    "Table '${dto.code}' seat capacity (${requested}) must equal the number of attached seats ($seatCount)"
                )
            }
            return requested ?: seatCount
        }

        return requested
            ?: throw VenuesException.ValidationFailure(
                "Table '${dto.code}' must specify seat capacity when no seat definitions are provided"
            )
    }

    private fun ensureUnique(values: List<String>, label: String) {
        val duplicates = values.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw VenuesException.ValidationFailure("Duplicate $label detected: ${duplicates.joinToString(", ")}")
        }
    }

    private data class ChartStats(val seatCount: Int, val gaCapacity: Int, val zoneCount: Int)

    private fun calculateChartStats(chartId: UUID): ChartStats {
        val seatCount = chartSeatRepository.countByZoneChartId(chartId).toInt()
        val zoneCount = chartZoneRepository.countByChartId(chartId).toInt()
        val gaCapacity = gaAreaRepository.sumCapacityByChartId(chartId)?.toInt() ?: 0

        return ChartStats(seatCount, gaCapacity, zoneCount)
    }
}

