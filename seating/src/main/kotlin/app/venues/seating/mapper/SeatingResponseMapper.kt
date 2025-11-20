package app.venues.seating.mapper

import app.venues.seating.domain.*
import app.venues.seating.model.*
import org.springframework.stereotype.Component

/**
 * Maps domain entities to REST response models.
 * Handles internal service layer mapping only.
 */
@Component
class SeatingResponseMapper {

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
            totalCapacity = seatCount + gaCapacity,
            zoneCount = zoneCount,
            seatCount = seatCount,
            createdAt = chart.createdAt.toString(),
            updatedAt = chart.lastModifiedAt.toString()
        )
    }

    /**
     * Map chart entity to detailed hierarchical response.
     */
    fun toDetailedResponse(
        chart: SeatingChart,
        venueName: String,
        allZones: List<ChartZone>
    ): SeatingChartDetailedResponse {
        val rootZones = allZones
            .filter { it.parentZone == null }
            .sortedBy { it.name }
            .map { toZoneResponse(it) }

        return SeatingChartDetailedResponse(
            id = chart.id,
            venueId = chart.venueId,
            name = chart.name,
            width = chart.width,
            height = chart.height,
            backgroundUrl = chart.backgroundUrl,
            rootZones = rootZones,
            createdAt = chart.createdAt.toString(),
            updatedAt = chart.lastModifiedAt.toString()
        )
    }

    /**
     * Map zone entity to response with recursive children.
     */
    fun toZoneResponse(zone: ChartZone): ZoneResponse {
        return ZoneResponse(
            id = zone.id ?: throw IllegalStateException("Zone ID is required but was null when mapping to ZoneResponse"),
            parentZoneId = zone.parentZone?.id,
            name = zone.name,
            code = zone.code,
            x = zone.x,
            y = zone.y,
            rotation = zone.rotation,
            boundaryPath = zone.boundaryPath,
            displayColor = zone.displayColor,
            seatCount = zone.seats.size,
            tableCount = zone.tables.size,
            gaCount = zone.gaAreas.size,
            childZones = zone.childZones.map { toZoneResponse(it) },
            seats = zone.seats.map { toSeatResponse(it) },
            tables = zone.tables.map { toTableResponse(it) },
            gaAreas = zone.gaAreas.map { toGaAreaResponse(it) }
        )
    }

    /**
     * Map seat entity to response.
     */
    fun toSeatResponse(seat: ChartSeat): SeatResponse {
        return SeatResponse(
            id = seat.id ?: error("Seat ID cannot be null"),
            zoneId = seat.zone.id ?: error("Zone ID cannot be null"),
            tableId = seat.table?.id,
            code = seat.code,
            rowLabel = seat.rowLabel,
            seatNumber = seat.seatNumber,
            categoryKey = seat.categoryKey,
            x = seat.x,
            y = seat.y,
            rotation = seat.rotation,
            isAccessible = seat.isAccessible,
            isObstructed = seat.isObstructedView
        )
    }

    /**
     * Map table entity to response.
     */
    fun toTableResponse(table: ChartTable): TableResponse {
        return TableResponse(
            id = table.id ?: error("Table ID cannot be null"),
            zoneId = table.zone.id ?: error("Zone ID cannot be null"),
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

    /**
     * Map GA area entity to response.
     */
    fun toGaAreaResponse(ga: GeneralAdmissionArea): GaAreaResponse {
        return GaAreaResponse(
            id = ga.id ?: error("GA Area ID cannot be null"),
            zoneId = ga.zone.id ?: error("Zone ID cannot be null"),
            code = ga.code,
            name = ga.name,
            capacity = ga.capacity,
            boundaryPath = ga.boundaryPath,
            displayColor = ga.displayColor
        )
    }
}

