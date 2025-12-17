package app.venues.seating.mapper

import app.venues.seating.api.dto.*
import app.venues.seating.domain.*
import app.venues.seating.model.BackgroundTransform
import app.venues.seating.model.SeatingChartDetailedResponse
import app.venues.seating.model.SeatingChartOverviewResponse
import app.venues.seating.model.SeatingChartResponse
import org.springframework.stereotype.Component

/**
 * Maps domain entities to REST response models.
 * Handles internal service layer mapping only.
 *
 * Uses unified structure DTOs from seating-api for consistency
 * with the public cached structure endpoint.
 */
@Component
class SeatingResponseMapper {

    /**
     * Map chart entity to overview response.
     */
    fun toOverviewResponse(
        chart: SeatingChart,
    ): SeatingChartOverviewResponse {
        return SeatingChartOverviewResponse(
            id = chart.id,
            name = chart.name,
        )
    }

    /**
     * Map chart entity to summary response.
     */
    fun toResponse(
        chart: SeatingChart,
        venueName: String,
        seatCount: Int,
        gaCapacity: Int,
        zoneCount: Int
    ): SeatingChartResponse {
        return SeatingChartResponse(
            id = chart.id,
            venueId = chart.venueId,
            venueName = venueName,
            name = chart.name,
            width = chart.width,
            height = chart.height,
            backgroundUrl = chart.backgroundUrl,
            backgroundTransform = BackgroundTransformMapper.fromJson(chart.backgroundTransformJson),
            totalCapacity = seatCount + gaCapacity,
            zoneCount = zoneCount,
            seatCount = seatCount,
            createdAt = chart.createdAt.toString(),
            updatedAt = chart.lastModifiedAt.toString()
        )
    }

    /**
     * Map chart entity to detailed hierarchical response.
     *
     * Uses the same ZoneStructureDto as the public cached endpoint for consistency.
     */
    fun toDetailedResponse(
        chart: SeatingChart,
        allZones: List<ChartZone>,
        landmarks: List<ChartLandmark>
    ): SeatingChartDetailedResponse {
        val rootZones = allZones
            .filter { it.parentZone == null }
            .sortedBy { it.name }
            .map { toZoneStructureDto(it) }

        val landmarkDtos = landmarks.map { toLandmarkDto(it) }

        return SeatingChartDetailedResponse(
            id = chart.id,
            venueId = chart.venueId,
            name = chart.name,
            width = chart.width,
            height = chart.height,
            backgroundUrl = chart.backgroundUrl,
            backgroundTransform = BackgroundTransformMapper.fromJson(chart.backgroundTransformJson)?.let {
                BackgroundTransform(it.x, it.y, it.scale, it.opacity)
            },
            rootZones = rootZones,
            landmarks = landmarkDtos,
            createdAt = chart.createdAt.toString(),
            updatedAt = chart.lastModifiedAt.toString()
        )
    }

    /**
     * Map zone entity to unified structure DTO with recursive children.
     *
     * This is the same structure used by the public cached endpoint.
     */
    private fun toZoneStructureDto(zone: ChartZone): ZoneStructureDto {
        return ZoneStructureDto(
            id = zone.id
                ?: throw IllegalStateException("Zone ID is required but was null when mapping to ZoneStructureDto"),
            name = zone.name,
            code = zone.code,
            x = zone.x,
            y = zone.y,
            rotation = zone.rotation,
            boundaryPath = zone.boundaryPath,
            displayColor = zone.displayColor,
            children = zone.childZones.map { toZoneStructureDto(it) },
            seats = zone.seats.map { toSeatStructureDto(it) },
            tables = zone.tables.map { toTableStructureDto(it) },
            gaAreas = zone.gaAreas.map { toGaAreaStructureDto(it) }
        )
    }

    /**
     * Map seat entity to unified structure DTO.
     */
    private fun toSeatStructureDto(seat: ChartSeat): SeatStructureDto {
        return SeatStructureDto(
            id = seat.id ?: error("Seat ID cannot be null"),
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

    /**
     * Map table entity to unified structure DTO.
     */
    private fun toTableStructureDto(table: ChartTable): TableStructureDto {
        return TableStructureDto(
            id = table.id ?: error("Table ID cannot be null"),
            code = table.code,
            tableNumber = table.tableNumber,
            seatCapacity = table.seatCapacity,
            categoryKey = table.categoryKey,
            shape = table.shape.name,
            x = table.x,
            y = table.y,
            width = table.width,
            height = table.height,
            rotation = table.rotation
        )
    }

    /**
     * Map GA area entity to unified structure DTO.
     */
    private fun toGaAreaStructureDto(ga: GeneralAdmissionArea): GaAreaStructureDto {
        return GaAreaStructureDto(
            id = ga.id ?: error("GA Area ID cannot be null"),
            code = ga.code,
            name = ga.name,
            capacity = ga.capacity,
            categoryKey = ga.categoryKey,
            boundaryPath = ga.boundaryPath,
            displayColor = ga.displayColor
        )
    }

    /**
     * Map landmark entity to unified DTO.
     */
    private fun toLandmarkDto(landmark: ChartLandmark): LandmarkDto {
        return LandmarkDto(
            id = landmark.id ?: error("Landmark ID cannot be null"),
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

