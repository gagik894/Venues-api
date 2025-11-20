package app.venues.seating.api.controller

import app.venues.common.constants.AppConstants
import app.venues.common.exception.VenuesException
import app.venues.common.model.ApiResponse
import app.venues.seating.model.*
import app.venues.seating.service.SeatingService
import app.venues.shared.persistence.util.PageableMapper
import app.venues.staff.api.StaffSecurityFacade
import app.venues.venue.api.VenueApi
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
 * 3. Controller calls StaffSecurityFacade to check venue management permission
 * 4. If authorized, controller delegates to service layer
 *
 * Permission Logic (via StaffSecurityFacade):
 * - SUPER_ADMIN → can manage any venue
 * - Organization OWNER/ADMIN → can manage venues in their organizations
 * - Venue MANAGER → can manage assigned venues
 */
@RestController
@RequestMapping("/api/v1/venues/{venueId}/seating-charts")
@Tag(name = "Venue Seating Charts", description = "Seating chart management for staff members")
@PreAuthorize("isAuthenticated()")
class VenueSeatingController(
    private val seatingService: SeatingService,
    private val staffSecurityFacade: StaffSecurityFacade,
    private val venueApi: VenueApi
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
        requireVenueManagementPermission(staffId, venueId)

        val chart = seatingService.createSeatingChart(venueId, request)
        return ApiResponse.success(chart, "Created successfully")
    }

    @GetMapping
    @Operation(summary = "Get venue charts")
    fun getSeatingCharts(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<SeatingChartResponse>> {
        requireVenueManagementPermission(staffId, venueId)

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
        requireVenueManagementPermission(staffId, venueId)

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
        requireVenueManagementPermission(staffId, venueId)

        val chart = seatingService.updateSeatingChart(chartId, venueId, request)
        return ApiResponse.success(chart, "Updated successfully")
    }

    @DeleteMapping("/{chartId}")
    @Operation(summary = "Delete chart")
    fun deleteSeatingChart(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        requireVenueManagementPermission(staffId, venueId)

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
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: ZoneRequest
    ): ApiResponse<ZoneResponse> {
        requireVenueManagementPermission(staffId, venueId)

        val zone = seatingService.addZone(chartId, request)
        return ApiResponse.success(zone, "Zone added")
    }

    @PostMapping("/{chartId}/tables")
    fun addTable(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: TableRequest
    ): ApiResponse<TableResponse> {
        requireVenueManagementPermission(staffId, venueId)

        val table = seatingService.addTable(chartId, request)
        return ApiResponse.success(table, "Table added")
    }

    @PostMapping("/{chartId}/ga-areas")
    fun addGaArea(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: GaAreaRequest
    ): ApiResponse<GaAreaResponse> {
        requireVenueManagementPermission(staffId, venueId)

        val ga = seatingService.addGaArea(chartId, request)
        return ApiResponse.success(ga, "GA Area added")
    }

    @PostMapping("/{chartId}/seats")
    fun addSeat(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: SeatRequest
    ): ApiResponse<SeatResponse> {
        requireVenueManagementPermission(staffId, venueId)

        val seat = seatingService.addSeat(chartId, request)
        return ApiResponse.success(seat, "Seat added")
    }

    @PostMapping("/{chartId}/seats/batch")
    fun addSeatsBatch(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: BatchSeatRequest
    ): ApiResponse<List<SeatResponse>> {
        requireVenueManagementPermission(staffId, venueId)

        val seats = seatingService.addSeatsBatch(chartId, request)
        return ApiResponse.success(seats, "${seats.size} seats added")
    }

    @DeleteMapping("/{chartId}/seats/{seatId}")
    fun deleteSeat(
        @PathVariable venueId: UUID,
        @PathVariable chartId: UUID,
        @PathVariable seatId: Long,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        requireVenueManagementPermission(staffId, venueId)

        seatingService.deleteSeat(seatId)
        return ApiResponse.success(Unit, "Deleted successfully")
    }

    // ===========================================
    // PRIVATE HELPERS
    // ===========================================

    /**
     * Checks if staff has permission to manage the specified venue.
     *
     * Uses StaffSecurityFacade which checks:
     * 1. Is staff a SUPER_ADMIN? → Allow
     * 2. Is staff OWNER/ADMIN of venue's organization? → Allow
     * 3. Does staff have explicit venue MANAGER permission? → Allow
     * 4. Otherwise → Deny
     *
     * @param staffId Staff UUID from JWT
     * @param venueId Venue UUID from path
     * @throws VenuesException.AuthorizationFailure if staff cannot manage venue
     */
    private fun requireVenueManagementPermission(staffId: UUID, venueId: UUID) {
        // Get venue's organization ID (needed for permission check)
        val organizationId = venueApi.getVenueOrganizationId(venueId)
            ?: throw VenuesException.ResourceNotFound(
                "Venue not found or has no organization",
                AppConstants.ErrorCode.NOT_FOUND.code
            )

        // Check permission via facade
        if (!staffSecurityFacade.canManageVenue(staffId, venueId, organizationId)) {
            logger.warn { "Staff $staffId attempted to manage venue $venueId without permission" }
            throw VenuesException.AuthorizationFailure(
                "You don't have permission to manage this venue",
                AppConstants.ErrorCode.AUTHORIZATION_FAILED.code

            )
        }
    }
}
