package app.venues.seating.service

import app.venues.common.exception.VenuesException
import app.venues.seating.api.dto.*
import app.venues.seating.domain.*
import app.venues.seating.repository.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for building static chart structure optimized for caching.
 *
 * Builds a recursive tree of zones containing seats, tables, GA areas,
 * and landmarks. Uses bulk fetching to avoid N+1 queries.
 *
 * Output is designed to be cached (Cache-Control: public, max-age=86400)
 * as it contains only static visual/structural data with no dynamic state.
 */
@Service
@Transactional(readOnly = true)
class ChartStructureService(
    private val seatingChartRepository: SeatingChartRepository,
    private val chartZoneRepository: ChartZoneRepository,
    private val chartSeatRepository: ChartSeatRepository,
    private val chartTableRepository: ChartTableRepository,
    private val generalAdmissionAreaRepository: GeneralAdmissionAreaRepository,
    private val chartLandmarkRepository: ChartLandmarkRepository
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Get complete static chart structure for caching.
     *
     * Performs bulk queries to avoid N+1:
     * 1. Fetch chart
     * 2. Fetch all zones for chart
     * 3. Fetch all seats for chart
     * 4. Fetch all tables for chart
     * 5. Fetch all GA areas for chart
     * 6. Fetch all landmarks for chart
     *
     * Then builds recursive tree in memory.
     *
     * @param chartId The chart UUID
     * @return Static chart structure ready for caching
     * @throws VenuesException.ResourceNotFound if chart not found
     */
    fun getStaticChartStructure(chartId: UUID): StaticChartStructureResponse {
        logger.debug { "Building static chart structure for chart: $chartId" }

        // 1. Fetch chart
        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found") }

        // 2. Bulk fetch all data (6 queries total - no N+1)
        val allZones = chartZoneRepository.findByChartId(chartId)
        val allSeats = chartSeatRepository.findByChartId(chartId)
        val allTables = chartTableRepository.findByChartId(chartId)
        val allGaAreas = generalAdmissionAreaRepository.findByChartId(chartId)
        val allLandmarks = chartLandmarkRepository.findByChartId(chartId)

        // 3. Build lookup maps for efficient grouping
        val seatsByZoneId = allSeats.groupBy { it.zone.id!! }
        val tablesByZoneId = allTables.groupBy { it.zone.id!! }
        val gaAreasByZoneId = allGaAreas.groupBy { it.zone.id!! }
        val childZonesByParentId = allZones.groupBy { it.parentZone?.id }

        // 4. Build recursive zone tree (only root zones at top level)
        val rootZones = allZones.filter { it.parentZone == null }
        val zoneStructures = rootZones.map { zone ->
            buildZoneStructure(
                zone = zone,
                childZonesByParentId = childZonesByParentId,
                seatsByZoneId = seatsByZoneId,
                tablesByZoneId = tablesByZoneId,
                gaAreasByZoneId = gaAreasByZoneId
            )
        }

        // 5. Map landmarks
        val landmarks = allLandmarks.map { toLandmarkDto(it) }

        logger.info {
            "Static chart structure built: ${allZones.size} zones, ${allSeats.size} seats, " +
                    "${allTables.size} tables, ${allGaAreas.size} GA areas, ${allLandmarks.size} landmarks"
        }

        return StaticChartStructureResponse(
            chartId = chart.id,
            chartName = chart.name,
            width = chart.width,
            height = chart.height,
            backgroundUrl = chart.backgroundUrl,
            zones = zoneStructures,
            landmarks = landmarks
        )
    }

    /**
     * Recursively builds zone structure with children, seats, tables, and GA areas.
     */
    private fun buildZoneStructure(
        zone: ChartZone,
        childZonesByParentId: Map<Long?, List<ChartZone>>,
        seatsByZoneId: Map<Long, List<ChartSeat>>,
        tablesByZoneId: Map<Long, List<ChartTable>>,
        gaAreasByZoneId: Map<Long, List<GeneralAdmissionArea>>
    ): ZoneStructureDto {
        val zoneId = zone.id!!

        // Recursively build child zones
        val children = childZonesByParentId[zoneId]?.map { childZone ->
            buildZoneStructure(
                zone = childZone,
                childZonesByParentId = childZonesByParentId,
                seatsByZoneId = seatsByZoneId,
                tablesByZoneId = tablesByZoneId,
                gaAreasByZoneId = gaAreasByZoneId
            )
        } ?: emptyList()

        // Map seats for this zone
        val seats = seatsByZoneId[zoneId]?.map { toSeatStructureDto(it) } ?: emptyList()

        // Map tables for this zone
        val tables = tablesByZoneId[zoneId]?.map { toTableStructureDto(it) } ?: emptyList()

        // Map GA areas for this zone
        val gaAreas = gaAreasByZoneId[zoneId]?.map { toGaAreaStructureDto(it) } ?: emptyList()

        return ZoneStructureDto(
            id = zoneId,
            name = zone.name,
            code = zone.code,
            x = zone.x,
            y = zone.y,
            rotation = zone.rotation,
            boundaryPath = zone.boundaryPath,
            displayColor = zone.displayColor,
            children = children,
            seats = seats,
            tables = tables,
            gaAreas = gaAreas
        )
    }

    private fun toSeatStructureDto(seat: ChartSeat): SeatStructureDto {
        return SeatStructureDto(
            id = seat.id!!,
            code = seat.code,
            rowLabel = seat.rowLabel,
            seatNumber = seat.seatNumber,
            categoryKey = seat.categoryKey,
            x = seat.x,
            y = seat.y,
            rotation = seat.rotation,
            isAccessible = seat.isAccessible,
            isObstructed = seat.isObstructedView,
            tableId = seat.table?.id
        )
    }

    private fun toTableStructureDto(table: ChartTable): TableStructureDto {
        return TableStructureDto(
            id = table.id!!,
            code = table.code,
            tableNumber = table.tableNumber,
            seatCapacity = table.seatCapacity,
            shape = table.shape.name,
            x = table.x,
            y = table.y,
            width = table.width,
            height = table.height,
            rotation = table.rotation
        )
    }

    private fun toGaAreaStructureDto(gaArea: GeneralAdmissionArea): GaAreaStructureDto {
        return GaAreaStructureDto(
            id = gaArea.id!!,
            code = gaArea.code,
            name = gaArea.name,
            capacity = gaArea.capacity,
            categoryKey = gaArea.categoryKey,
            boundaryPath = gaArea.boundaryPath,
            displayColor = gaArea.displayColor
        )
    }

    private fun toLandmarkDto(landmark: ChartLandmark): LandmarkDto {
        return LandmarkDto(
            id = landmark.id!!,
            label = landmark.label,
            type = landmark.type.name,
            shapeType = landmark.shapeType.name,
            x = landmark.x,
            y = landmark.y,
            width = landmark.width,
            height = landmark.height,
            rotation = landmark.rotation,
            boundaryPath = landmark.boundaryPath,
            iconKey = landmark.iconKey
        )
    }
}
