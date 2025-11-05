package app.venues.seating.api.mapper

import app.venues.seating.api.dto.LevelResponse
import app.venues.seating.api.dto.SeatResponse
import app.venues.seating.api.dto.SeatingChartDetailedResponse
import app.venues.seating.api.dto.SeatingChartResponse
import app.venues.seating.domain.Level
import app.venues.seating.domain.Seat
import app.venues.seating.domain.SeatingChart
import app.venues.seating.repository.LevelRepository
import app.venues.seating.repository.SeatRepository
import org.springframework.stereotype.Component

/**
 * Mapper for converting between Seating entities and DTOs.
 *
 * Handles bidirectional mapping for all seating-related objects.
 */
@Component
class SeatingMapper {

    /**
     * Convert SeatingChart entity to SeatingChartResponse DTO.
     * Note: venueName must be fetched separately via venue service if needed
     */
    fun toResponse(
        chart: SeatingChart,
        levelRepository: LevelRepository,
        seatRepository: SeatRepository,
        venueName: String? = null
    ): SeatingChartResponse {
        return SeatingChartResponse(
            id = chart.id!!,
            venueId = chart.venueId,
            venueName = venueName ?: "Unknown", // Venue name must be provided from venue module
            name = chart.name,
            seatIndicatorSize = chart.seatIndicatorSize,
            levelIndicatorSize = chart.levelIndicatorSize,
            backgroundUrl = chart.backgroundUrl,
            totalCapacity = chart.getTotalCapacity(levelRepository, seatRepository),
            levelCount = chart.getLevels(levelRepository).size,
            seatCount = chart.getSeats(seatRepository).size,
            createdAt = chart.createdAt.toString(),
            lastModifiedAt = chart.lastModifiedAt.toString()
        )
    }

    /**
     * Convert SeatingChart entity to detailed response with levels.
     * Note: venueName must be fetched separately via venue service if needed
     */
    fun toDetailedResponse(
        chart: SeatingChart,
        levelRepository: LevelRepository,
        seatRepository: SeatRepository,
        venueName: String? = null
    ): SeatingChartDetailedResponse {
        // Get top-level sections (no parent)
        val topLevels = chart.getLevels(levelRepository)
            .filter { it.parentLevel == null }
            .sortedBy { it.levelName }
            .map { toLevelResponse(it, includeChildren = true) }

        return SeatingChartDetailedResponse(
            id = chart.id!!,
            venueId = chart.venueId,
            venueName = venueName ?: "Unknown", // Venue name must be provided from venue module
            name = chart.name,
            seatIndicatorSize = chart.seatIndicatorSize,
            levelIndicatorSize = chart.levelIndicatorSize,
            backgroundUrl = chart.backgroundUrl,
            totalCapacity = chart.getTotalCapacity(levelRepository, seatRepository),
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

