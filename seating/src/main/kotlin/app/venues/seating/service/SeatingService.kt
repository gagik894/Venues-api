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
    private val chartLandmarkRepository: ChartLandmarkRepository,
    private val eventApi: app.venues.event.api.EventApi,
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
            backgroundUrl = request.backgroundUrl,
            backgroundTransformJson = BackgroundTransformMapper.toJson(request.backgroundTransform)
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
            backgroundUrl = request.chart.backgroundUrl,
            backgroundTransformJson = BackgroundTransformMapper.toJson(request.chart.backgroundTransform)
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
        chart.backgroundTransformJson = BackgroundTransformMapper.toJson(request.backgroundTransform)

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
        throw VenuesException.ValidationFailure(
            "Layout replacement is disabled. Clone the chart for major changes and apply visual-only updates in place."
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

        if (eventApi.seatingChartInUse(chartId)) {
            throw VenuesException.ValidationFailure("Cannot delete chart: it is referenced by events/sessions")
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

    /**
     * Clone an existing chart into a new chart (new IDs, same codes/geometry by default).
     * Used for major changes while keeping existing charts stable for active sessions.
     */
    fun cloneSeatingChart(
        venueId: UUID,
        sourceChartId: UUID,
        request: CloneSeatingChartRequest
    ): SeatingChartDetailedResponse {
        val source = seatingChartRepository.findById(sourceChartId)
            .orElseThrow { VenuesException.ResourceNotFound("Source chart not found") }

        if (source.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Chart does not belong to venue")
        }

        if (seatingChartRepository.existsByVenueIdAndName(venueId, request.name)) {
            throw VenuesException.ResourceConflict("Chart name already exists")
        }

        val newChart = SeatingChart(
            venueId = venueId,
            name = request.name,
            width = source.width,
            height = source.height,
            backgroundUrl = request.backgroundUrl ?: source.backgroundUrl,
            styleConfigJson = source.styleConfigJson,
            backgroundTransformJson = source.backgroundTransformJson
        )

        // Clone zones (two-pass to wire hierarchy)
        val sourceZones = chartZoneRepository.findByChartId(sourceChartId)
        val newZonesByOldId: Map<Long, ChartZone> = sourceZones.associate { src ->
            val zoneCopy = ChartZone(
                chart = newChart,
                parentZone = null,
                name = src.name,
                code = src.code,
                x = src.x,
                y = src.y,
                rotation = src.rotation,
                boundaryPath = src.boundaryPath,
                displayColor = src.displayColor
            )
            (src.id ?: error("Zone ID cannot be null")) to zoneCopy
        }

        sourceZones.forEach { src ->
            val newZone = newZonesByOldId.getValue(src.id ?: error("Zone ID cannot be null"))
            val parentId = src.parentZone?.id
            if (parentId != null) {
                val parent = newZonesByOldId[parentId]
                    ?: throw VenuesException.ValidationFailure("Parent zone $parentId missing during clone")
                parent.addChildZone(newZone)
            } else {
                newChart.addZone(newZone)
            }
        }

        // Clone tables
        val sourceTables = chartTableRepository.findByChartId(sourceChartId)
        val newTablesByOldId: Map<Long, ChartTable> = sourceTables.associate { src ->
            val zone =
                newZonesByOldId[src.zone.id ?: error("Zone ID null for table")] ?: error("Zone not found for table")
            val tableCopy = ChartTable(
                zone = zone,
                tableNumber = src.tableNumber,
                code = src.code,
                seatCapacity = src.seatCapacity,
                categoryKey = src.categoryKey,
                shape = src.shape,
                x = src.x,
                y = src.y,
                width = src.width,
                height = src.height,
                rotation = src.rotation
            )
            zone.addTable(tableCopy)
            (src.id ?: error("Table ID cannot be null")) to tableCopy
        }

        // Clone GA areas
        val sourceGa = gaAreaRepository.findByChartId(sourceChartId)
        sourceGa.forEach { src ->
            val zone = newZonesByOldId[src.zone.id ?: error("Zone ID null for GA")] ?: error("Zone not found for GA")
            val gaCopy = GeneralAdmissionArea(
                zone = zone,
                name = src.name,
                code = src.code,
                capacity = src.capacity,
                categoryKey = src.categoryKey,
                boundaryPath = src.boundaryPath,
                displayColor = src.displayColor
            )
            zone.addGaArea(gaCopy)
        }

        // Clone seats
        val sourceSeats = chartSeatRepository.findByChartId(sourceChartId)
        sourceSeats.forEach { src ->
            val zone =
                newZonesByOldId[src.zone.id ?: error("Zone ID null for seat")] ?: error("Zone not found for seat")
            val table = src.table?.id?.let { newTablesByOldId[it] }
            val seatCopy = ChartSeat(
                zone = zone,
                table = table,
                rowLabel = src.rowLabel,
                seatNumber = src.seatNumber,
                code = src.code,
                categoryKey = src.categoryKey,
                isAccessible = src.isAccessible,
                isObstructedView = src.isObstructedView,
                x = src.x,
                y = src.y,
                rotation = src.rotation
            )
            zone.addSeat(seatCopy)
            table?.attachSeat(seatCopy)
        }

        // Clone landmarks
        val sourceLandmarks = chartLandmarkRepository.findByChartId(sourceChartId)

        val saved = seatingChartRepository.save(newChart)

        if (sourceLandmarks.isNotEmpty()) {
            val landmarkCopies = sourceLandmarks.map { src ->
                ChartLandmark(
                    chart = saved,
                    label = src.label,
                    type = src.type,
                    shapeType = src.shapeType,
                    x = src.x,
                    y = src.y,
                    width = src.width,
                    height = src.height,
                    rotation = src.rotation,
                    boundaryPath = src.boundaryPath,
                    iconKey = src.iconKey
                )
            }
            chartLandmarkRepository.saveAll(landmarkCopies)
        }

        logger.info { "Cloned seating chart $sourceChartId into ${saved.id}" }
        return getSeatingChartDetailed(saved.id ?: error("Cloned chart ID null after save"))
    }

    /**
     * Apply visual-only updates that do not alter business keys or relationships.
     */
    fun updateVisuals(
        chartId: UUID,
        venueId: UUID,
        request: SeatingChartVisualUpdateRequest
    ): SeatingChartDetailedResponse {
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Chart not found") }
        if (chart.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Chart does not belong to venue")
        }

        // Zones
        if (request.zones.isNotEmpty()) {
            val zoneIds = request.zones.map { it.id }
            val zones = chartZoneRepository.findAllById(zoneIds)
            if (zones.size != zoneIds.toSet().size) {
                throw VenuesException.ValidationFailure("One or more zones not found for this chart")
            }
            zones.forEach { zone ->
                val upd = request.zones.first { it.id == zone.id }
                upd.name?.let { zone.name = it }
                upd.x?.let { zone.x = it }
                upd.y?.let { zone.y = it }
                upd.rotation?.let { zone.rotation = it }
                upd.boundaryPath?.let { zone.boundaryPath = it }
                upd.displayColor?.let { zone.displayColor = it }
            }
            chartZoneRepository.saveAll(zones)
        }

        // Seats
        if (request.seats.isNotEmpty()) {
            val seatIds = request.seats.map { it.id }
            val seats = chartSeatRepository.findAllById(seatIds)
            if (seats.size != seatIds.toSet().size) {
                throw VenuesException.ValidationFailure("One or more seats not found for this chart")
            }
            seats.forEach { seat ->
                val upd = request.seats.first { it.id == seat.id }
                // Ensure seat belongs to chart
                if (seat.zone.chart.id != chartId) {
                    throw VenuesException.ValidationFailure("Seat ${seat.id} does not belong to chart $chartId")
                }
                upd.x?.let { seat.x = it }
                upd.y?.let { seat.y = it }
                upd.rotation?.let { seat.rotation = it }
                upd.isAccessible?.let { seat.isAccessible = it }
                upd.isObstructed?.let { seat.isObstructedView = it }
                upd.categoryKey?.let { seat.categoryKey = it }
            }
            chartSeatRepository.saveAll(seats)
        }

        // Tables
        if (request.tables.isNotEmpty()) {
            val tableIds = request.tables.map { it.id }
            val tables = chartTableRepository.findAllById(tableIds)
            if (tables.size != tableIds.toSet().size) {
                throw VenuesException.ValidationFailure("One or more tables not found for this chart")
            }
            tables.forEach { table ->
                if (table.zone.chart.id != chartId) {
                    throw VenuesException.ValidationFailure("Table ${table.id} does not belong to chart $chartId")
                }
                val upd = request.tables.first { it.id == table.id }
                upd.x?.let { table.x = it }
                upd.y?.let { table.y = it }
                upd.width?.let { table.width = it }
                upd.height?.let { table.height = it }
                upd.rotation?.let { table.rotation = it }
                upd.shape?.let { table.shape = TableShape.valueOf(it.uppercase()) }
                upd.categoryKey?.let { table.categoryKey = it }
            }
            chartTableRepository.saveAll(tables)
        }

        // GA Areas
        if (request.gaAreas.isNotEmpty()) {
            val gaIds = request.gaAreas.map { it.id }
            val gaAreas = gaAreaRepository.findAllById(gaIds)
            if (gaAreas.size != gaIds.toSet().size) {
                throw VenuesException.ValidationFailure("One or more GA areas not found for this chart")
            }
            gaAreas.forEach { ga ->
                if (ga.zone.chart.id != chartId) {
                    throw VenuesException.ValidationFailure("GA area ${ga.id} does not belong to chart $chartId")
                }
                val upd = request.gaAreas.first { it.id == ga.id }
                upd.capacity?.let { ga.updateCapacity(it) }
                upd.boundaryPath?.let { ga.boundaryPath = it }
                upd.displayColor?.let { ga.displayColor = it }
                upd.categoryKey?.let { ga.categoryKey = it }
            }
            gaAreaRepository.saveAll(gaAreas)
        }

        // Landmarks
        request.landmarks?.let { landmarkUpdates ->
            if (landmarkUpdates.deleteIds.isNotEmpty()) {
                val toDelete = chartLandmarkRepository.findAllById(landmarkUpdates.deleteIds)
                toDelete.forEach {
                    if (it.chart.id != chartId) {
                        throw VenuesException.ValidationFailure("Landmark ${it.id} does not belong to chart $chartId")
                    }
                }
                chartLandmarkRepository.deleteAllInBatch(toDelete)
            }
            if (landmarkUpdates.upserts.isNotEmpty()) {
                val existing = landmarkUpdates.upserts.filter { it.id != null }.mapNotNull { it.id }
                val existingEntities =
                    if (existing.isEmpty()) emptyList() else chartLandmarkRepository.findAllById(existing)
                val existingById = existingEntities.associateBy { it.id }

                val toSave = landmarkUpdates.upserts.map { upd ->
                    if (upd.id == null) {
                        ChartLandmark(
                            chart = chart,
                            label = upd.label,
                            type = LandmarkType.valueOf(upd.type.uppercase()),
                            shapeType = LandmarkShapeType.valueOf(upd.shapeType.uppercase()),
                            x = upd.x,
                            y = upd.y,
                            width = upd.width,
                            height = upd.height,
                            rotation = upd.rotation,
                            boundaryPath = upd.boundaryPath,
                            iconKey = upd.iconKey
                        )
                    } else {
                        val entity = existingById[upd.id]
                            ?: throw VenuesException.ValidationFailure("Landmark ${upd.id} not found")
                        if (entity.chart.id != chartId) {
                            throw VenuesException.ValidationFailure("Landmark ${entity.id} does not belong to chart $chartId")
                        }
                        entity.label = upd.label
                        entity.type = LandmarkType.valueOf(upd.type.uppercase())
                        entity.shapeType = LandmarkShapeType.valueOf(upd.shapeType.uppercase())
                        entity.x = upd.x
                        entity.y = upd.y
                        entity.width = upd.width
                        entity.height = upd.height
                        entity.rotation = upd.rotation
                        entity.boundaryPath = upd.boundaryPath
                        entity.iconKey = upd.iconKey
                        entity
                    }
                }
                chartLandmarkRepository.saveAll(toSave)
            }
        }

        logger.info { "Applied visual updates to chart $chartId" }
        return getSeatingChartDetailed(chartId)
    }
}

