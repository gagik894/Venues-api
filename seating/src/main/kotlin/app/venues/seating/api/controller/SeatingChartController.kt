package app.venues.seating.api.controller

import app.venues.common.exception.VenuesException
import app.venues.common.model.ApiResponse
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.GaInfoDto
import app.venues.seating.api.dto.SeatInfoDto
import app.venues.seating.api.dto.SectionInfoDto
import app.venues.seating.api.dto.TableInfoDto
import app.venues.seating.model.SeatingChartDetailedResponse
import app.venues.seating.service.SeatingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

/**
 * Public seating chart viewing controller.
 * Provides read-only access to chart structure and component information.
 */
@RestController
@RequestMapping("/api/v1/seating-charts")
@Tag(name = "Seating Charts", description = "Public seating chart viewing")
class SeatingChartController(
    private val seatingApi: SeatingApi,
    private val seatingService: SeatingService
) {

    @GetMapping("/{chartId}")
    @Operation(summary = "Get seating chart structure")
    fun getSeatingChart(@PathVariable chartId: UUID): ApiResponse<SeatingChartDetailedResponse> {
        val chart = seatingService.getSeatingChartDetailed(chartId)
        return ApiResponse.success(chart, "Retrieved successfully")
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

    @GetMapping("/by-code/{code}")
    @Operation(summary = "Get seat by code")
    fun getSeatByCode(@PathVariable code: String): ApiResponse<SeatInfoDto> {
        val seat = seatingApi.getSeatInfoByCode(code)
            ?: throw VenuesException.ResourceNotFound("Seat not found")
        return ApiResponse.success(seat, "Retrieved successfully")
    }
}
