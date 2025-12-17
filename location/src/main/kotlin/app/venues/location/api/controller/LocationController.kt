package app.venues.location.api.controller

import app.venues.common.model.ApiResponse
import app.venues.location.api.dto.CityCompact
import app.venues.location.api.dto.CityResponse
import app.venues.location.api.dto.RegionResponse
import app.venues.location.service.LocationService
import app.venues.shared.i18n.LocaleHelper
import app.venues.shared.persistence.util.PageableMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
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
        val language = LocaleHelper.currentLanguage()
        logger.debug { "GET /api/v1/locations/regions (lang: $language)" }
        val regions = locationService.getAllActiveRegions(language)
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
        val language = LocaleHelper.currentLanguage()
        logger.debug { "GET /api/v1/locations/regions/$id (lang: $language)" }
        val region = locationService.getRegionById(id, language)
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
        val language = LocaleHelper.currentLanguage()
        logger.debug { "GET /api/v1/locations/regions/code/$code (lang: $language)" }
        val region = locationService.getRegionByCode(code, language)
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
        val language = LocaleHelper.currentLanguage()
        logger.debug { "GET /api/v1/locations/cities (lang: $language)" }
        val cities = locationService.getAllActiveCities(language)
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
        val language = LocaleHelper.currentLanguage()
        logger.debug { "GET /api/v1/locations/cities/$slug (lang: $language)" }
        val city = locationService.getCityBySlug(slug, language)
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
     * @param regionCode Parent region code
     * @return List of cities in the region
     */
    @GetMapping("/regions/{regionCode}/cities")
    @Operation(
        summary = "Get cities by region",
        description = "Returns all active cities in a specific region"
    )
    fun getCitiesByRegion(@PathVariable regionCode: String): ApiResponse<List<CityResponse>> {
        val language = LocaleHelper.currentLanguage()
        logger.debug { "GET /api/v1/locations/regions/$regionCode/cities (lang: $language)" }
        val cities = locationService.getCitiesByRegionCode(regionCode, language)
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
        val language = LocaleHelper.currentLanguage()
        logger.debug { "GET /api/v1/locations/cities/search?q=$q (lang: $language)" }
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val cities = locationService.searchCities(q, pageable, language)
        return ApiResponse.success(
            data = cities,
            message = "Search completed successfully"
        )
    }

    /**
     * Get compact city list for dropdowns.
     *
     * Returns lightweight representation suitable for UI dropdowns.
     * Language resolved from Accept-Language header.
     *
     * @return List of compact city data
     */
    @GetMapping("/cities/compact")
    @Operation(
        summary = "Get compact city list",
        description = "Returns lightweight city list for dropdowns and selectors. Localization via Accept-Language header."
    )
    fun getCitiesCompact(): ApiResponse<List<CityCompact>> {
        val language = LocaleHelper.currentLanguage()
        logger.debug { "GET /api/v1/locations/cities/compact (Accept-Language: $language)" }
        val cities = locationService.getCitiesCompact(language)
        return ApiResponse.success(
            data = cities,
            message = "Compact city list retrieved successfully"
        )
    }

}

