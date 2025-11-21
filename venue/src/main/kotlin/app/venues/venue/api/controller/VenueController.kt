package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
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
 * REST controller for public venue operations.
 * Uses UUIDs for primary access, slugs for SEO-friendly alternative.
 * All endpoints support localization via lang parameter.
 */
@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Venues", description = "Public venue discovery and information")
class VenueController(
    private val venueService: VenueService
) {

    @GetMapping
    @Operation(
        summary = "List venues",
        description = "Returns paginated list of active venues. Use 'lang' for localization (hy, en, ru)."
    )
    fun listVenues(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDirection: String?,
        @Parameter(description = "Language code (hy, en, ru)")
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<Page<VenueResponse>> {
        val pageable = PageableMapper.createPageable(
            limit = limit,
            offset = offset,
            sortBy = sortBy,
            sortDirection = sortDirection,
            allowedSortFields = setOf("name", "createdAt")
        )

        val venues = venueService.listVenues(pageable, lang)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get venue by ID",
        description = "Returns detailed venue information. Use 'lang' for localization."
    )
    fun getVenue(
        @PathVariable id: UUID,
        @Parameter(description = "Language code (hy, en, ru)")
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<VenueDetailResponse> {
        val venue = venueService.getVenue(id, lang)
        return ApiResponse.success(venue, "Venue retrieved successfully")
    }

    @GetMapping("/slug/{slug}")
    @Operation(
        summary = "Get venue by slug (SEO-friendly)",
        description = "Returns detailed venue information using URL-friendly slug. Use 'lang' for localization."
    )
    fun getVenueBySlug(
        @PathVariable slug: String,
        @Parameter(description = "Language code (hy, en, ru)")
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<VenueDetailResponse> {
        val venue = venueService.getVenueBySlug(slug, lang)
        return ApiResponse.success(venue, "Venue retrieved successfully")
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search venues",
        description = "Search venues by name. Supports partial matching, case-insensitive."
    )
    fun searchVenues(
        @Parameter(description = "Search query")
        @RequestParam("q") query: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @Parameter(description = "Language code (hy, en, ru)")
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<Page<VenueResponse>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.searchVenues(query, pageable, lang)
        return ApiResponse.success(venues, "Search completed successfully")
    }

    @GetMapping("/city/{citySlug}")
    @Operation(
        summary = "List venues by city",
        description = "Returns venues in a specific city (e.g., 'yerevan', 'gyumri')."
    )
    fun listVenuesByCity(
        @PathVariable citySlug: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @Parameter(description = "Language code (hy, en, ru)")
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<Page<VenueResponse>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.listVenuesByCity(citySlug, pageable, lang)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @GetMapping("/region/{regionCode}")
    @Operation(
        summary = "List venues by region",
        description = "Returns venues in a region using ISO code (e.g., 'AM-ER', 'AM-SH')."
    )
    fun listVenuesByRegion(
        @PathVariable regionCode: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @Parameter(description = "Language code (hy, en, ru)")
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<Page<VenueResponse>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.listVenuesByRegion(regionCode, pageable, lang)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @GetMapping("/category/{categoryCode}")
    @Operation(
        summary = "List venues by category",
        description = "Returns venues in a category using code (e.g., 'OPERA', 'MUSEUM')."
    )
    fun listVenuesByCategory(
        @PathVariable categoryCode: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @Parameter(description = "Language code (hy, en, ru)")
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<Page<VenueResponse>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.listVenuesByCategory(categoryCode, pageable, lang)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @GetMapping("/categories")
    @Operation(
        summary = "List venue categories",
        description = "Returns all active venue categories for filtering."
    )
    fun listCategories(
        @Parameter(description = "Language code (hy, en, ru)")
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<List<VenueCategoryDto>> {
        val categories = venueService.listCategories(lang)
        return ApiResponse.success(categories, "Categories retrieved successfully")
    }
}