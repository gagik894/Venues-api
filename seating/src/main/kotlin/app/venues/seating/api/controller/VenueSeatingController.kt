package app.venues.seating.api.controller

import app.venues.common.model.ApiResponse
import app.venues.seating.model.*
import app.venues.seating.service.SeatingService
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.service.VenueSecurityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*

/**
 * Venue seating chart management controller.
 *
 * Authorization Pattern:
 * 1. @PreAuthorize checks authentication (STAFF or SUPER_ADMIN)
 * 2. Controller uses @RequestAttribute to get staffId from JWT
 * 3. Controller calls VenueSecurityService to check venue management permission
 * 4. If authorized, controller delegates to service layer
 *
 * Permission Logic (via VenueSecurityService):
 * - SUPER_ADMIN → can manage any venue
 * - Organization OWNER/ADMIN → can manage venues in their organizations
 * - Venue MANAGER → can manage assigned venues
 */
@RestController
@RequestMapping("/api/v1/staff/venues/{venueId}/seating-charts")
@Tag(name = "Venue Seating Charts", description = "Seating chart management for staff members")
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
class VenueSeatingController(
    private val seatingService: SeatingService,
    private val venueSecurityService: VenueSecurityService
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // SEATING CHART
    // ===========================================

    @PostMapping
    @Operation(summary = "Create seating chart", description = "Requires venue management permission")
    fun createSeatingChart(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: SeatingChartRequest, principal: Principal
    ): ApiResponse<SeatingChartResponse> {
        // Check permission: can staff manage this venue?
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val chart = seatingService.createSeatingChart(venueId, request)
        return ApiResponse.success(chart, "Created successfully")
    }

    @PostMapping("/layout")
    @Operation(summary = "Create seating chart with layout")
    fun createSeatingChartWithLayout(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: SeatingChartLayoutRequest
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val chart = seatingService.createSeatingChartWithLayout(venueId, request)
        return ApiResponse.success(chart, "Created successfully")
    }

    @GetMapping
    @Operation(summary = "Get seating chart overview")
    fun getSeatingChartsOverview(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<List<SeatingChartOverviewResponse>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        val charts = seatingService.getSeatingChartsOverviewByVenue(venueId)
        return ApiResponse.success(charts, "Retrieved successfully")
    }

    @GetMapping("/details")
    @Operation(summary = "Get venue charts")
    fun getSeatingCharts(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<SeatingChartResponse>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val charts = seatingService.getSeatingChartsByVenue(venueId, pageable)
        return ApiResponse.success(charts, "Retrieved successfully")
    }

    @GetMapping("/{chartId}")
    @Operation(summary = "Get detailed chart")
    fun getSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val chart = seatingService.getSeatingChartDetailed(chartId)
        return ApiResponse.success(chart, "Retrieved successfully")
    }

    @PutMapping("/{chartId}")
    @Operation(summary = "Update chart details")
    fun updateSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: SeatingChartRequest
    ): ApiResponse<SeatingChartResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val chart = seatingService.updateSeatingChart(chartId, venueId, request)
        return ApiResponse.success(chart, "Updated successfully")
    }

    @PutMapping("/{chartId}/layout")
    @Operation(summary = "Replace seating chart layout")
    fun replaceSeatingChartLayout(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: SeatingChartLayoutRequest
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val chart = seatingService.replaceSeatingChartLayout(chartId, venueId, request)
        return ApiResponse.success(chart, "Layout replaced")
    }

    @DeleteMapping("/{chartId}")
    @Operation(summary = "Delete chart")
    fun deleteSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        seatingService.deleteSeatingChart(chartId, venueId)
        return ApiResponse.success(Unit, "Deleted successfully")
    }
}
