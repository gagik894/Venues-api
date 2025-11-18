package app.venues.seating.api.mapper

import app.venues.seating.api.dto.*
import app.venues.seating.domain.*
import org.springframework.stereotype.Component

@Component
class SeatingMapper {

    // ==========================================
    // Cross-Module Info Mappings (For SeatingApi)
    // ==========================================

    fun toSeatInfoDto(seat: ChartSeat): SeatInfoDto {
        return SeatInfoDto(
            id = seat.id!!,
            code = seat.code,
            seatNumber = seat.seatNumber,
            rowLabel = seat.rowLabel,
            zoneId = seat.zone.id!!,
            zoneName = seat.zone.name,
            categoryKey = seat.categoryKey
        )
    }

    fun toTableInfoDto(table: ChartTable): TableInfoDto {
        return TableInfoDto(
            id = table.id!!,
            code = table.code,
            tableNumber = table.tableNumber,
            seatCapacity = table.seatCapacity,
            zoneId = table.zone.id!!,
            zoneName = table.zone.name
        )
    }

    fun toSectionInfoDto(zone: ChartZone): SectionInfoDto {
        return SectionInfoDto(
            id = zone.id!!,
            code = zone.code,
            name = zone.name
        )
    }

    fun toGaInfoDto(ga: GeneralAdmissionArea): GaInfoDto {
        return GaInfoDto(
            id = ga.id!!,
            code = ga.code,
            name = ga.name,
            capacity = ga.capacity,
            zoneId = ga.zone.id!!
        )
    }

    // ==========================================
    // Response Mappings (For Controllers)
    // ==========================================

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
            updatedAt = chart.updatedAt.toString()
        )
    }

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
            updatedAt = chart.updatedAt.toString()
        )
    }

    fun toZoneResponse(zone: ChartZone): ZoneResponse {
        return ZoneResponse(
            id = zone.id!!,
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
            gaCount = zone.generalAdmissionAreas.size,
            childZones = zone.childZones.map { toZoneResponse(it) },
            seats = zone.seats.map { toSeatResponse(it) },
            tables = zone.tables.map { toTableResponse(it) },
            gaAreas = zone.generalAdmissionAreas.map { toGaAreaResponse(it) }
        )
    }

    fun toSeatResponse(seat: ChartSeat): SeatResponse {
        return SeatResponse(
            id = seat.id!!,
            zoneId = seat.zone.id!!,
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

    fun toTableResponse(table: ChartTable): TableResponse {
        return TableResponse(
            id = table.id!!,
            zoneId = table.zone.id!!,
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

    fun toGaAreaResponse(ga: GeneralAdmissionArea): GaAreaResponse {
        return GaAreaResponse(
            id = ga.id!!,
            zoneId = ga.zone.id!!,
            code = ga.code,
            name = ga.name,
            capacity = ga.capacity,
            boundaryPath = ga.boundaryPath,
            displayColor = ga.displayColor
        )
    }
}