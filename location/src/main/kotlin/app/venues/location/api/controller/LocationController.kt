package app.venues.location.api.controller

import app.venues.common.model.ApiResponse
import app.venues.location.api.dto.*
import app.venues.location.service.LocationService
import app.venues.shared.persistence.util.PageableMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for Location (Region & City) operations.
 *
 * Provides endpoints for:
 * - Public access to regions and cities (for venue/event location selection)
 * - Admin operations for managing reference data
 *
 * Endpoint Structure:
 * - GET /api/v1/locations/regions - List all active regions
 * - GET /api/v1/locations/regions/{id} - Get region by ID
 * - GET /api/v1/locations/cities - List all active cities
 * - GET /api/v1/locations/cities/{slug} - Get city by slug
 * - GET /api/v1/locations/regions/{regionId}/cities - List cities in region
 *
 * Admin Endpoints (require ADMIN role):
 * - POST /api/v1/admin/locations/regions - Create region
 * - PUT /api/v1/admin/locations/regions/{id} - Update region
 * - POST /api/v1/admin/locations/cities - Create city
 * - PUT /api/v1/admin/locations/cities/{id} - Update city
 *
 * Caching:
 * Public endpoints should be cached (regions/cities rarely change).
 * Consider adding @Cacheable annotations at service layer.
 */
@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Locations", description = "Reference location data (regions and cities)")
class LocationController(
    private val locationService: LocationService
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // PUBLIC REGION ENDPOINTS
    // ===========================================

    /**
     * Get all active regions.
     *
     * Returns list of regions for venue/event location selection.
     * Results are ordered by display order.
     *
     * @return List of active regions
     */
    @GetMapping("/regions")
    @Operation(
        summary = "Get all active regions",
        description = "Returns list of active administrative regions for location selection"
    )
    fun getAllRegions(): ApiResponse<List<RegionResponse>> {
        logger.debug { "GET /api/v1/locations/regions" }
        val regions = locationService.getAllActiveRegions()
        return ApiResponse.success(
            data = regions,
            message = "Regions retrieved successfully"
        )
    }

    /**
     * Get region by ID.
     *
     * @param id Region ID
     * @return Region data
     */
    @GetMapping("/regions/{id}")
    @Operation(
        summary = "Get region by ID",
        description = "Returns detailed region information"
    )
    fun getRegionById(@PathVariable id: Long): ApiResponse<RegionResponse> {
        logger.debug { "GET /api/v1/locations/regions/$id" }
        val region = locationService.getRegionById(id)
        return ApiResponse.success(
            data = region,
            message = "Region retrieved successfully"
        )
    }

    /**
     * Get region by code.
     *
     * @param code ISO/government code (e.g., "AM-ER")
     * @return Region data
     */
    @GetMapping("/regions/code/{code}")
    @Operation(
        summary = "Get region by code",
        description = "Returns region by ISO/government code (e.g., AM-ER)"
    )
    fun getRegionByCode(@PathVariable code: String): ApiResponse<RegionResponse> {
        logger.debug { "GET /api/v1/locations/regions/code/$code" }
        val region = locationService.getRegionByCode(code)
        return ApiResponse.success(
            data = region,
            message = "Region retrieved successfully"
        )
    }

    // ===========================================
    // PUBLIC CITY ENDPOINTS
    // ===========================================

    /**
     * Get all active cities.
     *
     * Returns list of cities for venue/event location selection.
     * Results are ordered by display order.
     *
     * @return List of active cities
     */
    @GetMapping("/cities")
    @Operation(
        summary = "Get all active cities",
        description = "Returns list of active cities for location selection"
    )
    fun getAllCities(): ApiResponse<List<CityResponse>> {
        logger.debug { "GET /api/v1/locations/cities" }
        val cities = locationService.getAllActiveCities()
        return ApiResponse.success(
            data = cities,
            message = "Cities retrieved successfully"
        )
    }

    /**
     * Get city by slug.
     *
     * Primary lookup method for cities.
     * Example: /api/v1/locations/cities/gyumri
     *
     * @param slug City slug (URL-friendly identifier)
     * @return City data
     */
    @GetMapping("/cities/{slug}")
    @Operation(
        summary = "Get city by slug",
        description = "Returns city by URL-friendly slug (e.g., gyumri, yerevan)"
    )
    fun getCityBySlug(@PathVariable slug: String): ApiResponse<CityResponse> {
        logger.debug { "GET /api/v1/locations/cities/$slug" }
        val city = locationService.getCityBySlug(slug)
        return ApiResponse.success(
            data = city,
            message = "City retrieved successfully"
        )
    }

    /**
     * Get cities by region.
     *
     * Returns all active cities in a specific region.
     *
     * @param regionId Parent region ID
     * @return List of cities in the region
     */
    @GetMapping("/regions/{regionId}/cities")
    @Operation(
        summary = "Get cities by region",
        description = "Returns all active cities in a specific region"
    )
    fun getCitiesByRegion(@PathVariable regionId: Long): ApiResponse<List<CityResponse>> {
        logger.debug { "GET /api/v1/locations/regions/$regionId/cities" }
        val cities = locationService.getCitiesByRegion(regionId)
        return ApiResponse.success(
            data = cities,
            message = "Cities retrieved successfully"
        )
    }

    /**
     * Search cities by name (multilingual).
     *
     * Searches across all language names.
     *
     * @param q Search term
     * @param limit Page size
     * @param offset Page number
     * @return Page of matching cities
     */
    @GetMapping("/cities/search")
    @Operation(
        summary = "Search cities by name",
        description = "Search cities across all languages (partial match)"
    )
    fun searchCities(
        @RequestParam q: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<CityResponse>> {
        logger.debug { "GET /api/v1/locations/cities/search?q=$q" }
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val cities = locationService.searchCities(q, pageable)
        return ApiResponse.success(
            data = cities,
            message = "Search completed successfully"
        )
    }

    /**
     * Get compact city list for dropdowns.
     *
     * Returns lightweight representation suitable for UI dropdowns.
     *
     * @param lang Language code for names (default: en)
     * @return List of compact city data
     */
    @GetMapping("/cities/compact")
    @Operation(
        summary = "Get compact city list",
        description = "Returns lightweight city list for dropdowns and selectors"
    )
    fun getCitiesCompact(
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<List<CityCompact>> {
        logger.debug { "GET /api/v1/locations/cities/compact?lang=$lang" }
        val cities = locationService.getCitiesCompact(lang)
        return ApiResponse.success(
            data = cities,
            message = "Compact city list retrieved successfully"
        )
    }

    // ===========================================
    // ADMIN REGION ENDPOINTS
    // ===========================================

    /**
     * Get all regions (including inactive) for admin purposes.
     *
     * @return List of all regions
     */
    @GetMapping("/admin/regions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Get all regions (admin)",
        description = "Returns all regions including inactive ones (admin only)"
    )
    fun getAllRegionsAdmin(): ApiResponse<List<RegionResponse>> {
        logger.debug { "GET /api/v1/locations/admin/regions (admin)" }
        val regions = locationService.getAllRegions()
        return ApiResponse.success(
            data = regions,
            message = "All regions retrieved successfully"
        )
    }

    /**
     * Create a new region (admin only).
     *
     * @param request Region creation data
     * @return Created region
     */
    @PostMapping("/admin/regions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create region (admin)",
        description = "Create a new administrative region (admin only)"
    )
    fun createRegion(@Valid @RequestBody request: CreateRegionRequest): ApiResponse<RegionResponse> {
        logger.info { "POST /api/v1/locations/admin/regions (admin)" }
        val region = locationService.createRegion(request)
        return ApiResponse.success(
            data = region,
            message = "Region created successfully"
        )
    }

    /**
     * Update an existing region (admin only).
     *
     * @param id Region ID
     * @param request Update data
     * @return Updated region
     */
    @PutMapping("/admin/regions/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Update region (admin)",
        description = "Update an existing region (admin only)"
    )
    fun updateRegion(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateRegionRequest
    ): ApiResponse<RegionResponse> {
        logger.info { "PUT /api/v1/locations/admin/regions/$id (admin)" }
        val region = locationService.updateRegion(id, request)
        return ApiResponse.success(
            data = region,
            message = "Region updated successfully"
        )
    }

    // ===========================================
    // ADMIN CITY ENDPOINTS
    // ===========================================

    /**
     * Create a new city (admin only).
     *
     * @param request City creation data
     * @return Created city
     */
    @PostMapping("/admin/cities")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create city (admin)",
        description = "Create a new city (admin only)"
    )
    fun createCity(@Valid @RequestBody request: CreateCityRequest): ApiResponse<CityResponse> {
        logger.info { "POST /api/v1/locations/admin/cities (admin)" }
        val city = locationService.createCity(request)
        return ApiResponse.success(
            data = city,
            message = "City created successfully"
        )
    }

    /**
     * Update an existing city (admin only).
     *
     * @param id City ID
     * @param request Update data
     * @return Updated city
     */
    @PutMapping("/admin/cities/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Update city (admin)",
        description = "Update an existing city (admin only)"
    )
    fun updateCity(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCityRequest
    ): ApiResponse<CityResponse> {
        logger.info { "PUT /api/v1/locations/admin/cities/$id (admin)" }
        val city = locationService.updateCity(id, request)
        return ApiResponse.success(
            data = city,
            message = "City updated successfully"
        )
    }
}

