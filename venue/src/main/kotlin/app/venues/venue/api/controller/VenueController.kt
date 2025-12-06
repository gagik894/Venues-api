package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.shared.i18n.LocaleHelper
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.dto.VenueCategoryDto
import app.venues.venue.api.dto.VenueDetailResponse
import app.venues.venue.api.dto.VenueResponse
import app.venues.venue.service.VenueService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Public controller for venue discovery and browsing.
 *
 * All endpoints are publicly accessible (no authentication required).
 * Provides venue listing, search, and filtering by city/region/category.
 * Localization is handled via Accept-Language header (hy, en, ru).
 */
@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Venues", description = "Public venue discovery and browsing")
class VenueController(
    private val venueService: VenueService
) {

    @GetMapping
    @Operation(
        summary = "List venues",
        description = "Returns paginated list of active venues. Localization via Accept-Language header (hy, en, ru)."
    )
    fun listVenues(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDirection: String?
    ): ApiResponse<Page<VenueResponse>> {
        val language = LocaleHelper.currentLanguage()
        val pageable = PageableMapper.createPageable(
            limit = limit,
            offset = offset,
            sortBy = sortBy,
            sortDirection = sortDirection,
            allowedSortFields = setOf("name", "createdAt")
        )

        val venues = venueService.listVenues(pageable, language)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get venue by ID",
        description = "Detailed venue information. Localization via Accept-Language."
    )
    fun getVenue(@PathVariable id: UUID): ApiResponse<VenueDetailResponse> {
        val language = LocaleHelper.currentLanguage()
        val venue = venueService.getVenue(id, language)
        return ApiResponse.success(venue, "Venue retrieved successfully")
    }

    @GetMapping("/slug/{slug}")
    @Operation(
        summary = "Get venue by slug",
        description = "SEO-friendly access. Localization via Accept-Language."
    )
    fun getVenueBySlug(@PathVariable slug: String): ApiResponse<VenueDetailResponse> {
        val language = LocaleHelper.currentLanguage()
        val venue = venueService.getVenueBySlug(slug, language)
        return ApiResponse.success(venue, "Venue retrieved successfully")
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search venues",
        description = "Case-insensitive search by name."
    )
    fun searchVenues(
        @Parameter(description = "Search query") @RequestParam("q") query: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<VenueResponse>> {
        val language = LocaleHelper.currentLanguage()
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.searchVenues(query, pageable, language)
        return ApiResponse.success(venues, "Search completed successfully")
    }

    @GetMapping("/city/{citySlug}")
    @Operation(
        summary = "List venues by city",
        description = "Filter by city slug."
    )
    fun listVenuesByCity(
        @PathVariable citySlug: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<VenueResponse>> {
        val language = LocaleHelper.currentLanguage()
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.listVenuesByCity(citySlug, pageable, language)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @GetMapping("/region/{regionCode}")
    @Operation(
        summary = "List venues by region",
        description = "Filter by ISO region code."
    )
    fun listVenuesByRegion(
        @PathVariable regionCode: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<VenueResponse>> {
        val language = LocaleHelper.currentLanguage()
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.listVenuesByRegion(regionCode, pageable, language)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @GetMapping("/category/{categoryCode}")
    @Operation(
        summary = "List venues by category",
        description = "Filter by category code."
    )
    fun listVenuesByCategory(
        @PathVariable categoryCode: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<VenueResponse>> {
        val language = LocaleHelper.currentLanguage()
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.listVenuesByCategory(categoryCode, pageable, language)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @GetMapping("/categories")
    @Operation(
        summary = "List venue categories",
        description = "All active categories for filtering."
    )
    fun listCategories(): ApiResponse<List<VenueCategoryDto>> {
        val language = LocaleHelper.currentLanguage()
        val categories = venueService.listCategories(language)
        return ApiResponse.success(categories, "Categories retrieved successfully")
    }
}
