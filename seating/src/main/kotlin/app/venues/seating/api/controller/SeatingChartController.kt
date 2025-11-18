package app.venues.seating.api.controller

import app.venues.common.model.ApiResponse
import app.venues.seating.api.dto.*
import app.venues.seating.service.SeatingService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/v1/seating-charts")
@Tag(name = "Seating Charts", description = "Public seating chart viewing")
class SeatingChartController(
    private val seatingService: SeatingService
) {
    private val logger = KotlinLogging.logger {}

    @GetMapping("/{chartId}")
    @Operation(summary = "Get seating chart structure")
    fun getSeatingChart(@PathVariable chartId: UUID): ApiResponse<SeatingChartDetailedResponse> {
        val chart = seatingService.getSeatingChartDetailed(chartId)
        return ApiResponse.success(chart, "Retrieved successfully")
    }

    @GetMapping("/sections/{sectionId}")
    @Operation(summary = "Get section info")
    fun getSection(@PathVariable sectionId: Long): ApiResponse<SectionInfoDto> {
        val info = seatingService.getSectionInfo(sectionId)
            ?: throw app.venues.common.exception.VenuesException.ResourceNotFound("Section not found: $sectionId")
        return ApiResponse.success(info, "Retrieved successfully")
    }

    @GetMapping("/tables/{tableId}")
    @Operation(summary = "Get table info")
    fun getTable(@PathVariable tableId: Long): ApiResponse<TableInfoDto> {
        val info = seatingService.getTableInfo(tableId)
            ?: throw app.venues.common.exception.VenuesException.ResourceNotFound("Table not found: $tableId")
        return ApiResponse.success(info, "Retrieved successfully")
    }

    @GetMapping("/ga-areas/{gaId}")
    @Operation(summary = "Get GA area info")
    fun getGaArea(@PathVariable gaId: Long): ApiResponse<GaInfoDto> {
        val info = seatingService.getGaInfo(gaId)
            ?: throw app.venues.common.exception.VenuesException.ResourceNotFound("GA Area not found: $gaId")
        return ApiResponse.success(info, "Retrieved successfully")
    }

    @GetMapping("/seats/{seatId}")
    @Operation(summary = "Get seat by ID")
    fun getSeat(@PathVariable seatId: Long): ApiResponse<SeatInfoDto> {
        val seat = seatingService.getSeatInfo(seatId)
            ?: throw app.venues.common.exception.VenuesException.ResourceNotFound("Seat not found: $seatId")
        return ApiResponse.success(seat, "Retrieved successfully")
    }

    @GetMapping("/by-code/{code}")
    @Operation(summary = "Get seat by Business Key")
    fun getSeatByCode(@PathVariable code: String): ApiResponse<SeatInfoDto> {
        val seat = seatingService.getSeatInfoByCode(code)
            ?: throw app.venues.common.exception.VenuesException.ResourceNotFound("Seat not found: $code")
        return ApiResponse.success(seat, "Retrieved successfully")
    }
}