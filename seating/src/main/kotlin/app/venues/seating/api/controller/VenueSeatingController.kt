package app.venues.seating.api.controller

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
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

    @PostMapping("/layout")
    @Operation(summary = "Create seating chart with layout")
    @Auditable(action = "SEATING_CHART_CREATE", subjectType = "seating_chart")
    fun createSeatingChartWithLayout(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @Valid @RequestBody request: SeatingChartLayoutRequest
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)

        val chart = seatingService.createSeatingChartWithLayout(venueId, request)
        return ApiResponse.success(chart, "Created successfully")
    }

    @GetMapping
    @Operation(summary = "Get seating chart overview")
    fun getSeatingChartsOverview(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<List<SeatingChartOverviewResponse>> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)
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
        venueSecurityService.requireVenueViewPermission(staffId, venueId)

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val charts = seatingService.getSeatingChartsByVenue(venueId, pageable)
        return ApiResponse.success(charts, "Retrieved successfully")
    }

    @GetMapping("/{chartId}")
    @Operation(
        summary = "Get detailed chart",
        description = """
            Returns the full chart structure (zones, seats, tables, GA areas).

            Security note: the chartId must belong to the provided venueId; otherwise the request fails.
        """
    )
    fun getSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)

        val chart = seatingService.getSeatingChartDetailedForVenue(chartId, venueId)
        return ApiResponse.success(chart, "Retrieved successfully")
    }

    @PutMapping("/{chartId}")
    @Operation(
        summary = "Update chart details",
        description = """
            Updates chart metadata only: name, canvas size, background URL, and background transform.

            This endpoint does NOT modify the layout component tree (zones, seats, tables, GA areas).
            For layout changes, clone the chart and update the clone.
        """
    )
    @Auditable(action = "SEATING_CHART_UPDATE", subjectType = "seating_chart")
    fun updateSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @Valid @RequestBody request: SeatingChartRequest
    ): ApiResponse<SeatingChartResponse> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)

        val chart = seatingService.updateSeatingChart(chartId, venueId, request)
        return ApiResponse.success(chart, "Updated successfully")
    }

    @PutMapping("/{chartId}/layout")
    @Operation(
        summary = "Replace seating chart layout",
        description = """
            Disabled by design.

            Reason: replacing a layout changes inventory identifiers and can break existing sessions/bookings.
            Use POST /{chartId}/clone for major changes, then update visuals on the clone.
        """
    )
    @Auditable(action = "SEATING_CHART_REPLACE_LAYOUT", subjectType = "seating_chart")
    fun replaceSeatingChartLayout(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @Valid @RequestBody request: SeatingChartLayoutRequest
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)

        val chart = seatingService.replaceSeatingChartLayout(chartId, venueId, request)
        return ApiResponse.success(chart, "Layout replaced")
    }

    @PostMapping("/{chartId}/clone")
    @Operation(summary = "Clone seating chart for major changes")
    @Auditable(action = "SEATING_CHART_CLONE", subjectType = "seating_chart")
    fun cloneSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @Valid @RequestBody request: CloneSeatingChartRequest
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        val cloned = seatingService.cloneSeatingChart(venueId, chartId, request)
        return ApiResponse.success(cloned, "Cloned successfully")
    }

    @PatchMapping("/{chartId}/default-category")
    @Operation(
        summary = "Set default category for chart",
        description = "Bulk updates categoryKey for seats, tables, and GA areas when the chart is not in use."
    )
    @Auditable(action = "SEATING_DEFAULT_CATEGORY_UPDATE", subjectType = "seating_chart")
    fun updateDefaultCategory(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @Valid @RequestBody request: DefaultCategoryUpdateRequest
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        val chart = seatingService.updateDefaultCategory(chartId, venueId, request)
        return ApiResponse.success(chart, "Default category updated")
    }

    @PatchMapping("/{chartId}/categories/selected")
    @Operation(
        summary = "Set category for selected seats/tables/GA areas",
        description = "Bulk updates categoryKey for the provided component IDs when the chart is not in use."
    )
    @Auditable(action = "SEATING_SELECTED_CATEGORY_UPDATE", subjectType = "seating_chart")
    fun updateSelectedCategories(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @Valid @RequestBody request: SelectiveCategoryUpdateRequest
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        val chart = seatingService.updateSelectedCategories(chartId, venueId, request)
        return ApiResponse.success(chart, "Categories updated for selected components")
    }

    @PatchMapping("/{chartId}/visuals")
    @Operation(
        summary = "Update visual attributes (non-destructive)",
        description = """
            Applies partial updates to visual rendering attributes for existing components.

            Always allowed fields (even when chart is in use):
            - Zones: name, x/y/rotation, boundaryPath, displayColor
            - Seats: x/y/rotation
            - Tables: x/y/width/height/rotation/shape
            - GA areas: boundaryPath, displayColor
            - Landmarks: create/update/delete

            When the chart is referenced by events/sessions, inventory semantics are frozen and these are rejected:
            - Seats: categoryKey, isAccessible, isObstructed
            - Tables: categoryKey
            - GA areas: capacity, categoryKey

            For those changes, clone the chart and update the clone.
        """
    )
    @Auditable(action = "SEATING_VISUALS_UPDATE", subjectType = "seating_chart")
    fun updateVisuals(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @AuditMetadata("request") @Valid @RequestBody request: SeatingChartVisualUpdateRequest
    ): ApiResponse<SeatingChartDetailedResponse> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        val chart = seatingService.updateVisuals(chartId, venueId, request)
        return ApiResponse.success(chart, "Visuals updated")
    }

    @DeleteMapping("/{chartId}")
    @Operation(summary = "Delete chart")
    @Auditable(action = "SEATING_CHART_DELETE", subjectType = "seating_chart")
    fun deleteSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)

        seatingService.deleteSeatingChart(chartId, venueId)
        return ApiResponse.success(Unit, "Deleted successfully")
    }
}
