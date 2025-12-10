package app.venues.location.api.controller

import app.venues.common.model.ApiResponse
import app.venues.location.api.dto.*
import app.venues.location.service.LocationService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Admin-only endpoints for managing reference location data.
 */
@RestController
@RequestMapping("/api/v1/locations/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Admin Locations", description = "Manage regions and cities (admin)")
class AdminLocationController(
    private val locationService: LocationService
) {
    private val logger = KotlinLogging.logger {}

    // =============================
    // Regions
    // =============================

    @GetMapping("/regions")
    @Operation(
        summary = "Get all regions (admin)",
        description = "Returns all regions including inactive ones (admin only)"
    )
    fun getAllRegions(): ApiResponse<List<RegionResponse>> {
        logger.debug { "GET /api/v1/locations/admin/regions" }
        val regions = locationService.getAllRegions()
        return ApiResponse.success(regions, "All regions retrieved successfully")
    }

    @PostMapping("/regions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create region (admin)",
        description = "Create a new administrative region (admin only)"
    )
    fun createRegion(@Valid @RequestBody request: CreateRegionRequest): ApiResponse<RegionResponse> {
        logger.info { "POST /api/v1/locations/admin/regions" }
        val region = locationService.createRegion(request)
        return ApiResponse.success(region, "Region created successfully")
    }

    @PutMapping("/regions/{code}")
    @Operation(
        summary = "Update region (admin)",
        description = "Update an existing region (admin only)"
    )
    fun updateRegion(
        @PathVariable code: String,
        @Valid @RequestBody request: UpdateRegionRequest
    ): ApiResponse<RegionResponse> {
        logger.info { "PUT /api/v1/locations/admin/regions/$code" }
        val region = locationService.updateRegion(code, request)
        return ApiResponse.success(region, "Region updated successfully")
    }

    // =============================
    // Cities
    // =============================

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create city (admin)",
        description = "Create a new city (admin only)"
    )
    fun createCity(@Valid @RequestBody request: CreateCityRequest): ApiResponse<CityResponse> {
        logger.info { "POST /api/v1/locations/admin/cities" }
        val city = locationService.createCity(request)
        return ApiResponse.success(city, "City created successfully")
    }

    @PutMapping("/cities/{slug}")
    @Operation(
        summary = "Update city (admin)",
        description = "Update an existing city (admin only)"
    )
    fun updateCity(
        @PathVariable slug: String,
        @Valid @RequestBody request: UpdateCityRequest
    ): ApiResponse<CityResponse> {
        logger.info { "PUT /api/v1/locations/admin/cities/$slug" }
        val city = locationService.updateCity(slug, request)
        return ApiResponse.success(city, "City updated successfully")
    }
}

