package app.venues.seating.api.controller

import app.venues.common.model.ApiResponse
import app.venues.seating.api.dto.LevelResponse
import app.venues.seating.api.dto.SeatResponse
import app.venues.seating.api.dto.SeatingChartDetailedResponse
import app.venues.seating.service.SeatingService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

/**
 * Public controller for viewing seating charts.
 *
 * Provides endpoints for:
 * - Viewing seating chart layouts
 * - Getting seat information for booking
 */
@RestController
@RequestMapping("/api/v1/seating-charts")
@Tag(name = "Seating Charts", description = "Public seating chart viewing")
class SeatingChartController(
    private val seatingService: SeatingService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get seating chart with full details.
     */
    @GetMapping("/{chartId}")
    @Operation(
        summary = "Get seating chart",
        description = "Get complete seating chart layout with all levels and seats"
    )
    fun getSeatingChart(@PathVariable chartId: UUID): ApiResponse<SeatingChartDetailedResponse> {
        logger.debug { "Public: Fetching seating chart: $chartId" }

        val chart = seatingService.getSeatingChartDetailed(chartId)

        return ApiResponse.success(
            data = chart,
            message = "Seating chart retrieved successfully"
        )
    }

    /**
     * Get level details.
     */
    @GetMapping("/levels/{levelId}")
    @Operation(
        summary = "Get level details",
        description = "Get detailed information about a specific level/section"
    )
    fun getLevel(@PathVariable levelId: Long): ApiResponse<LevelResponse> {
        logger.debug { "Public: Fetching level: $levelId" }

        val level = seatingService.getLevelById(levelId)

        return ApiResponse.success(
            data = level,
            message = "Level retrieved successfully"
        )
    }

    /**
     * Get seat by identifier.
     */
    @GetMapping("/levels/{levelId}/seats/{seatIdentifier}")
    @Operation(
        summary = "Get seat by identifier",
        description = "Get seat details using its identifier (e.g., 'A1', 'B12')"
    )
    fun getSeatByIdentifier(
        @PathVariable levelId: Long,
        @PathVariable seatIdentifier: String
    ): ApiResponse<SeatResponse> {
        logger.debug { "Public: Fetching seat: level=$levelId, identifier=$seatIdentifier" }

        val seat = seatingService.getSeatByIdentifier(levelId, seatIdentifier)

        return ApiResponse.success(
            data = seat,
            message = "Seat retrieved successfully"
        )
    }
}

