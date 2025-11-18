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
    // SEATING CHART
    // ===========================================

    @PostMapping
    @Operation(summary = "Create seating chart")
    fun createSeatingChart(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: SeatingChartRequest
    ): ApiResponse<SeatingChartResponse> {
        securityUtil.requireVenueOwnership(venueId)
        val chart = seatingService.createSeatingChart(venueId, request)
        return ApiResponse.success(chart, "Created successfully")
    }

    @GetMapping
    @Operation(summary = "Get venue charts")
    fun getSeatingCharts(
        @PathVariable venueId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<SeatingChartResponse>> {
        securityUtil.requireVenueOwnership(venueId)
        val pageable = PaginationUtil.createPageable(limit, offset)
        val charts = seatingService.getSeatingChartsByVenue(venueId, pageable)
        return ApiResponse.success(charts, "Retrieved successfully")
    }

    @GetMapping("/{chartId}")
    @Operation(summary = "Get detailed chart")
    fun getSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID
    ): ApiResponse<SeatingChartDetailedResponse> {
        securityUtil.requireVenueOwnership(venueId)
        val chart = seatingService.getSeatingChartDetailed(chartId)
        return ApiResponse.success(chart, "Retrieved successfully")
    }

    @PutMapping("/{chartId}")
    @Operation(summary = "Update chart details")
    fun updateSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: SeatingChartRequest
    ): ApiResponse<SeatingChartResponse> {
        securityUtil.requireVenueOwnership(venueId)
        val chart = seatingService.updateSeatingChart(chartId, venueId, request)
        return ApiResponse.success(chart, "Updated successfully")
    }

    @DeleteMapping("/{chartId}")
    @Operation(summary = "Delete chart")
    fun deleteSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID
    ): ApiResponse<Unit> {
        securityUtil.requireVenueOwnership(venueId)
        seatingService.deleteSeatingChart(chartId, venueId)
        return ApiResponse.success(Unit, "Deleted successfully")
    }

    // ===========================================
    // COMPONENTS (ZONES, TABLES, GA, SEATS)
    // ===========================================

    @PostMapping("/{chartId}/zones")
    fun addZone(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: ZoneRequest
    ): ApiResponse<ZoneResponse> {
        securityUtil.requireVenueOwnership(venueId)
        val zone = seatingService.addZone(chartId, request)
        return ApiResponse.success(zone, "Zone added")
    }

    @PostMapping("/{chartId}/tables")
    fun addTable(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: TableRequest
    ): ApiResponse<TableResponse> {
        securityUtil.requireVenueOwnership(venueId)
        val table = seatingService.addTable(chartId, request)
        return ApiResponse.success(table, "Table added")
    }

    @PostMapping("/{chartId}/ga-areas")
    fun addGaArea(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: GaAreaRequest
    ): ApiResponse<GaAreaResponse> {
        securityUtil.requireVenueOwnership(venueId)
        val ga = seatingService.addGaArea(chartId, request)
        return ApiResponse.success(ga, "GA Area added")
    }

    @PostMapping("/{chartId}/seats")
    fun addSeat(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: SeatRequest
    ): ApiResponse<SeatResponse> {
        securityUtil.requireVenueOwnership(venueId)
        val seat = seatingService.addSeat(chartId, request)
        return ApiResponse.success(seat, "Seat added")
    }

    @PostMapping("/{chartId}/seats/batch")
    fun addSeatsBatch(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @Valid @RequestBody request: BatchSeatRequest
    ): ApiResponse<List<SeatResponse>> {
        securityUtil.requireVenueOwnership(venueId)
        val seats = seatingService.addSeatsBatch(chartId, request)
        return ApiResponse.success(seats, "${seats.size} seats added")
    }

    @DeleteMapping("/{chartId}/seats/{seatId}")
    fun deleteSeat(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @PathVariable seatId: Long
    ): ApiResponse<Unit> {
        securityUtil.requireVenueOwnership(venueId)
        seatingService.deleteSeat(seatId)
        return ApiResponse.success(Unit, "Deleted successfully")
    }
}