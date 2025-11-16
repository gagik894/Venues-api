package app.venues.seating.api.mapper

import app.venues.seating.api.dto.*
import app.venues.seating.domain.Level
import app.venues.seating.domain.Seat
import app.venues.seating.domain.SeatingChart
import org.springframework.stereotype.Component

/**
 * Mapper for converting between Seating entities and DTOs.
 *
 * Follows clean architecture - mapper only converts data, does not fetch it.
 * All data fetching is done by the service layer before calling the mapper.
 */
@Component
class SeatingMapper {
    /**
     * Convert Level entity to LevelInfoDto.
     * This DTO is used for the public SeatingApi interface.
     * It correctly uses the entity's business logic methods.
     */
    fun toLevelInfoDto(level: Level): LevelInfoDto {
        return LevelInfoDto(
            id = level.id!!,
            levelName = level.levelName,
            levelIdentifier = level.levelIdentifier,
            capacity = level.capacity,
            isGeneralAdmission = level.isGeneralAdmission()
        )
    }

    /**
     * Convert Seat entity to SeatInfoDto.
     * This DTO is used for the public SeatingApi interface.
     * Assumes seat.level has been fetched by the service.
     */
    fun toSeatInfoDto(seat: Seat): SeatInfoDto {
        return SeatInfoDto(
            id = seat.id!!,
            seatIdentifier = seat.seatIdentifier,
            seatNumber = seat.seatNumber,
            rowLabel = seat.rowLabel,
            levelId = seat.level.id!!,
            levelName = seat.level.levelName
        )
    }

    /**
     * Convert SeatingChart entity to SeatingChartResponse DTO.
     * Service layer must provide all pre-fetched data.
     *
     * @param chart SeatingChart entity
     * @param levels Pre-fetched levels for this chart
     * @param seats Pre-fetched seats for this chart
     * @param venueName Venue name (optional, fetched via VenueApi)
     */
    fun toResponse(
        chart: SeatingChart,
        levels: List<Level>,
        seats: List<Seat>,
        venueName: String? = null
    ): SeatingChartResponse {
        val gaCapacity = levels.filter { it.isGeneralAdmission() }.sumOf { it.capacity ?: 0 }
        val seatedCapacity = seats.size
        val totalCapacity = gaCapacity + seatedCapacity

        return SeatingChartResponse(
            id = chart.id!!,
            venueId = chart.venueId,
            venueName = venueName ?: "Unknown",
            name = chart.name,
            seatIndicatorSize = chart.seatIndicatorSize,
            levelIndicatorSize = chart.levelIndicatorSize,
            backgroundUrl = chart.backgroundUrl,
            totalCapacity = totalCapacity,
            levelCount = levels.size,
            seatCount = seats.size,
            createdAt = chart.createdAt.toString(),
            lastModifiedAt = chart.lastModifiedAt.toString()
        )
    }

    /**
     * Convert SeatingChart entity to detailed response with levels.
     * Service layer must provide all pre-fetched data.
     *
     * @param chart SeatingChart entity
     * @param levels Pre-fetched levels for this chart
     * @param seats Pre-fetched seats for this chart
     * @param venueName Venue name (optional, fetched via VenueApi)
     */
    fun toDetailedResponse(
        chart: SeatingChart,
        levels: List<Level>,
        seats: List<Seat>,
        venueName: String? = null
    ): SeatingChartDetailedResponse {
        // Get top-level sections (no parent)
        val topLevels = levels
            .filter { it.parentLevel == null }
            .sortedBy { it.levelName }
            .map { toLevelResponse(it, includeChildren = true) }

        val gaCapacity = levels.filter { it.isGeneralAdmission() }.sumOf { it.capacity ?: 0 }
        val seatedCapacity = seats.size
        val totalCapacity = gaCapacity + seatedCapacity

        return SeatingChartDetailedResponse(
            id = chart.id!!,
            venueId = chart.venueId,
            venueName = venueName ?: "Unknown",
            name = chart.name,
            seatIndicatorSize = chart.seatIndicatorSize,
            levelIndicatorSize = chart.levelIndicatorSize,
            backgroundUrl = chart.backgroundUrl,
            totalCapacity = totalCapacity,
            levels = topLevels,
            createdAt = chart.createdAt.toString(),
            lastModifiedAt = chart.lastModifiedAt.toString()
        )
    }

    /**
     * Convert Level entity to LevelResponse DTO.
     */
    fun toLevelResponse(level: Level, includeChildren: Boolean = false): LevelResponse {
        val childLevels = if (includeChildren) {
            level.childLevels.sortedBy { it.levelName }.map { toLevelResponse(it, includeChildren = true) }
        } else {
            null
        }

        return LevelResponse(
            id = level.id!!,
            parentLevelId = level.parentLevel?.id,
            levelName = level.levelName,
            levelIdentifier = level.levelIdentifier,
            positionX = level.positionX,
            positionY = level.positionY,
            capacity = level.capacity,
            isGeneralAdmission = level.isGeneralAdmission(),
            isSeatedSection = level.isSeatedSection(),
            seatCount = level.seats.size,
            childLevels = childLevels,
            createdAt = level.createdAt.toString()
        )
    }

    /**
     * Convert Seat entity to SeatResponse DTO.
     */
    fun toSeatResponse(seat: Seat): SeatResponse {
        return SeatResponse(
            id = seat.id!!,
            levelId = seat.level.id!!,
            levelName = seat.level.levelName,
            seatIdentifier = seat.seatIdentifier,
            seatNumber = seat.seatNumber,
            rowLabel = seat.rowLabel,
            positionX = seat.positionX,
            positionY = seat.positionY,
            seatType = seat.seatType,
            fullDisplayName = seat.getFullDisplayName(),
            createdAt = seat.createdAt.toString()
        )
    }
}