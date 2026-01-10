package app.venues.seating.api.controller

import app.venues.common.exception.VenuesException
import app.venues.common.model.ApiResponse
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.*
import app.venues.seating.model.SeatingChartDetailedResponse
import app.venues.seating.service.ChartStructureService
import app.venues.seating.service.SeatingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Public seating chart viewing controller.
 * Provides read-only access to chart structure and component information.
 */
@RestController
@RequestMapping("/api/v1/seating-charts")
@Tag(name = "Seating Charts", description = "Public seating chart viewing")
class SeatingChartController(
    private val seatingApi: SeatingApi,
    private val seatingService: SeatingService,
    private val chartStructureService: ChartStructureService
) {

    @GetMapping("/{chartId}")
    @Operation(summary = "Get seating chart structure")
    fun getSeatingChart(@PathVariable chartId: UUID): ApiResponse<SeatingChartDetailedResponse> {
        val chart = seatingService.getSeatingChartDetailed(chartId)
        return ApiResponse.success(chart, "Retrieved successfully")
    }

    /**
     * Get static chart structure optimized for caching.
     *
     * Returns only visual/structural data (positions, shapes, labels) without
     * dynamic state (status, price). Use /sessions/{sessionId}/inventory for dynamic data.
     *
     * Response includes Cache-Control header for 24-hour caching.
     */
    @GetMapping("/{chartId}/structure")
    @Operation(
        summary = "Get static chart structure (cached)",
        description = """
            Returns the static visual structure of the seating chart optimized for caching.
            
            Includes:
            - Recursive zone hierarchy with boundaries
            - Seat positions (X, Y, rotation, labels)
            - Table shapes and positions
            - GA area boundaries
            - Landmarks (Stage, Exit, Bar, etc.)
            
            Does NOT include:
            - Availability status
            - Pricing information
            - Sold counts
            
            Use GET /sessions/{sessionId}/inventory for dynamic inventory state.
            
            This endpoint returns Cache-Control: public, max-age=86400 (24 hours).
        """
    )
    fun getChartStructure(
        @PathVariable chartId: UUID
    ): ResponseEntity<ApiResponse<StaticChartStructureResponse>> {
        val structure = chartStructureService.getStaticChartStructure(chartId)

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic())
            .body(ApiResponse.success(structure, "Static chart structure retrieved successfully"))
    }

    @GetMapping("/sections/{sectionId}")
    @Operation(summary = "Get section info")
    fun getSection(@PathVariable sectionId: Long): ApiResponse<SectionInfoDto> {
        val info = seatingApi.getSectionInfo(sectionId)
            ?: throw VenuesException.ResourceNotFound("Section not found")
        return ApiResponse.success(info, "Retrieved successfully")
    }

    @GetMapping("/tables/{tableId}")
    @Operation(summary = "Get table info")
    fun getTable(@PathVariable tableId: Long): ApiResponse<TableInfoDto> {
        val info = seatingApi.getTableInfo(tableId)
            ?: throw VenuesException.ResourceNotFound("Table not found")
        return ApiResponse.success(info, "Retrieved successfully")
    }

    @GetMapping("/ga-areas/{gaId}")
    @Operation(summary = "Get GA area info")
    fun getGaArea(@PathVariable gaId: Long): ApiResponse<GaInfoDto> {
        val info = seatingApi.getGaInfo(gaId)
            ?: throw VenuesException.ResourceNotFound("GA Area not found")
        return ApiResponse.success(info, "Retrieved successfully")
    }

    @GetMapping("/seats/{seatId}")
    @Operation(summary = "Get seat by ID")
    fun getSeat(@PathVariable seatId: Long): ApiResponse<SeatInfoDto> {
        val seat = seatingApi.getSeatInfo(seatId)
            ?: throw VenuesException.ResourceNotFound("Seat not found")
        return ApiResponse.success(seat, "Retrieved successfully")
    }

    @GetMapping("/{chartId}/seats/by-code/{code}")
    @Operation(summary = "Get seat by code")
    fun getSeatByCode(
        @PathVariable chartId: UUID,
        @PathVariable code: String
    ): ApiResponse<SeatInfoDto> {
        val seat = seatingApi.getSeatInfoByCode(chartId, code)
            ?: throw VenuesException.ResourceNotFound("Seat not found")
        return ApiResponse.success(seat, "Retrieved successfully")
    }
}
