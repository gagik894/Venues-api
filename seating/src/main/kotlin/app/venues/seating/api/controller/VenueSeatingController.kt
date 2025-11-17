package app.venues.seating.api.controller

import app.venues.common.model.ApiResponse
import app.venues.common.util.PaginationUtil
import app.venues.seating.api.dto.*
import app.venues.seating.service.SeatingService
import app.venues.shared.security.util.SecurityUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Controller for venue owner seating chart management.
 *
 * Provides endpoints for venue owners to:
 * - Create and manage seating charts
 * - Define levels (sections/areas)
 * - Add seats
 * - Manage translations
 */
@RestController
@RequestMapping("/api/v1/venues/{venueId}/seating-charts")
@Tag(name = "Venue Seating Charts", description = "Seating chart management for venue owners")
@PreAuthorize("hasRole('VENUE')")
class VenueSeatingController(
    private val seatingService: SeatingService,
    private val securityUtil: SecurityUtil
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // SEATING CHART MANAGEMENT
    // ===========================================

    /**
     * Create seating chart for venue.
     */
    @PostMapping
    @Operation(
        summary = "Create seating chart",
        description = "Create a new seating chart template for the venue (Venue owners only)"
    )
    fun createSeatingChart(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: SeatingChartRequest
    ): ApiResponse<SeatingChartResponse> {
        logger.debug { "Creating seating chart for venue: $venueId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val chart = seatingService.createSeatingChart(venueId, request)

        return ApiResponse.success(
            data = chart,
            message = "Seating chart created successfully"
        )
    }

    /**
     * Get seating charts for venue.
     */
    @GetMapping
    @Operation(
        summary = "Get seating charts",
        description = "Get all seating charts for the venue (Venue owners only)"
    )
    fun getSeatingCharts(
        @PathVariable venueId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<SeatingChartResponse>> {
        logger.debug { "Fetching seating charts for venue: $venueId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val pageable = PaginationUtil.createPageable(limit, offset)
        val charts = seatingService.getSeatingChartsByVenue(venueId, pageable)

        return ApiResponse.success(
            data = charts,
            message = "Seating charts retrieved successfully"
        )
    }

    /**
     * Get seating chart by ID.
     */
    @GetMapping("/{chartId}")
    @Operation(
        summary = "Get seating chart",
        description = "Get detailed seating chart with levels and seats (Venue owners only)"
    )
    fun getSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID
    ): ApiResponse<SeatingChartDetailedResponse> {
        logger.debug { "Fetching seating chart: $chartId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val chart = seatingService.getSeatingChartDetailed(chartId)

        return ApiResponse.success(
            data = chart,
            message = "Seating chart retrieved successfully"
        )
    }

    /**
     * Update seating chart.
     */
    @PutMapping("/{chartId}")
    @Operation(
        summary = "Update seating chart",
        description = "Update seating chart details (Venue owners only)"
    )
    fun updateSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: SeatingChartRequest
    ): ApiResponse<SeatingChartResponse> {
        logger.debug { "Updating seating chart: $chartId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val chart = seatingService.updateSeatingChart(chartId, venueId, request)

        return ApiResponse.success(
            data = chart,
            message = "Seating chart updated successfully"
        )
    }

    /**
     * Delete seating chart.
     */
    @DeleteMapping("/{chartId}")
    @Operation(
        summary = "Delete seating chart",
        description = "Delete a seating chart (Venue owners only)"
    )
    fun deleteSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID
    ): ApiResponse<Unit> {
        logger.debug { "Deleting seating chart: $chartId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        seatingService.deleteSeatingChart(chartId, venueId)

        return ApiResponse.success(
            data = Unit,
            message = "Seating chart deleted successfully"
        )
    }

    // ===========================================
    // LEVEL MANAGEMENT
    // ===========================================

    /**
     * Add level to seating chart.
     */
    @PostMapping("/{chartId}/levels")
    @Operation(
        summary = "Add level",
        description = "Add a section/area to the seating chart (Venue owners only)"
    )
    fun addLevel(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: LevelRequest
    ): ApiResponse<LevelResponse> {
        logger.debug { "Adding level to chart: $chartId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val level = seatingService.addLevel(chartId, request)

        return ApiResponse.success(
            data = level,
            message = "Level added successfully"
        )
    }

    /**
     * Update level.
     */
    @PutMapping("/{chartId}/levels/{levelId}")
    @Operation(
        summary = "Update level",
        description = "Update level details (Venue owners only)"
    )
    fun updateLevel(
        @PathVariable venueId: UUID,
        @PathVariable chartId: Long,
        @PathVariable levelId: Long,
        @Valid @RequestBody request: LevelRequest
    ): ApiResponse<LevelResponse> {
        logger.debug { "Updating level: $levelId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val level = seatingService.updateLevel(levelId, request)

        return ApiResponse.success(
            data = level,
            message = "Level updated successfully"
        )
    }

    /**
     * Delete level.
     */
    @DeleteMapping("/{chartId}/levels/{levelId}")
    @Operation(
        summary = "Delete level",
        description = "Delete a level (Venue owners only)"
    )
    fun deleteLevel(
        @PathVariable venueId: UUID,
        @PathVariable chartId: Long,
        @PathVariable levelId: Long
    ): ApiResponse<Unit> {
        logger.debug { "Deleting level: $levelId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        seatingService.deleteLevel(levelId)

        return ApiResponse.success(
            data = Unit,
            message = "Level deleted successfully"
        )
    }

    // ===========================================
    // SEAT MANAGEMENT
    // ===========================================

    /**
     * Add seat to chart.
     */
    @PostMapping("/{chartId}/seats")
    @Operation(
        summary = "Add seat",
        description = "Add a single seat to the chart (Venue owners only)"
    )
    fun addSeat(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: SeatRequest
    ): ApiResponse<SeatResponse> {
        logger.debug { "Adding seat to chart: $chartId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val seat = seatingService.addSeat(chartId, request)

        return ApiResponse.success(
            data = seat,
            message = "Seat added successfully"
        )
    }

    /**
     * Batch add seats.
     */
    @PostMapping("/{chartId}/seats/batch")
    @Operation(
        summary = "Batch add seats",
        description = "Add multiple seats at once (Venue owners only)"
    )
    fun addSeats(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: BatchSeatRequest
    ): ApiResponse<List<SeatResponse>> {
        logger.debug { "Batch adding ${request.seats.size} seats to chart: $chartId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val seats = seatingService.addSeats(chartId, request)

        return ApiResponse.success(
            data = seats,
            message = "${seats.size} seats added successfully"
        )
    }

    /**
     * Get seats for a level.
     */
    @GetMapping("/{chartId}/levels/{levelId}/seats")
    @Operation(
        summary = "Get level seats",
        description = "Get all seats in a level (Venue owners only)"
    )
    fun getSeatsByLevel(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @PathVariable levelId: Long,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<SeatResponse>> {
        logger.debug { "Fetching seats for level: $levelId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val pageable = PaginationUtil.createPageable(limit, offset)
        val seats = seatingService.getSeatsByLevel(levelId, pageable)

        return ApiResponse.success(
            data = seats,
            message = "Seats retrieved successfully"
        )
    }

    /**
     * Delete seat.
     */
    @DeleteMapping("/{chartId}/seats/{seatId}")
    @Operation(
        summary = "Delete seat",
        description = "Delete a seat (Venue owners only)"
    )
    fun deleteSeat(
        @PathVariable venueId: UUID,
        @PathVariable chartId: Long,
        @PathVariable seatId: Long
    ): ApiResponse<Unit> {
        logger.debug { "Deleting seat: $seatId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        seatingService.deleteSeat(seatId)

        return ApiResponse.success(
            data = Unit,
            message = "Seat deleted successfully"
        )
    }
}

