package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.shared.i18n.LocaleHelper
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.dto.VenueCategoryDto
import app.venues.venue.api.dto.VenueDetailResponse
import app.venues.venue.api.dto.VenueIdentifierDto
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
 * All endpoints support localization via Accept-Language header.
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

    @GetMapping("/identifiers")
    @Operation(
        summary = "Get venue identifiers for ISR",
        description = """
            Returns minimal venue identifiers for Next.js ISR static path generation.
            
            Use cases:
            - getStaticPaths(): Use 'id' (UUID) as immutable cache key
            - Middleware routing: Use 'customDomain' → 'id' mapping
            
            UUIDs are preferred over slugs because they never change,
            ensuring ISR cached pages remain valid after venue renames.
        """
    )
    fun getVenueIdentifiers(): ApiResponse<List<VenueIdentifierDto>> {
        val identifiers = venueService.getVenueIdentifiers()
        return ApiResponse.success(identifiers, "Venue identifiers retrieved successfully")
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get venue by ID",
        description = "Returns detailed venue information. Localization via Accept-Language header."
    )
    fun getVenue(
        @PathVariable id: UUID
    ): ApiResponse<VenueDetailResponse> {
        val language = LocaleHelper.currentLanguage()
        val venue = venueService.getVenue(id, language)
        return ApiResponse.success(venue, "Venue retrieved successfully")
    }

    @GetMapping("/slug/{slug}")
    @Operation(
        summary = "Get venue by slug (SEO-friendly)",
        description = "Returns detailed venue information using URL-friendly slug. Localization via Accept-Language header."
    )
    fun getVenueBySlug(
        @PathVariable slug: String
    ): ApiResponse<VenueDetailResponse> {
        val language = LocaleHelper.currentLanguage()
        val venue = venueService.getVenueBySlug(slug, language)
        return ApiResponse.success(venue, "Venue retrieved successfully")
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search venues",
        description = "Search venues by name. Supports partial matching, case-insensitive. Localization via Accept-Language header."
    )
    fun searchVenues(
        @Parameter(description = "Search query")
        @RequestParam("q") query: String,
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
        description = "Returns venues in a specific city (e.g., 'yerevan', 'gyumri'). Localization via Accept-Language header."
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
        description = "Returns venues in a region using ISO code (e.g., 'AM-ER', 'AM-SH'). Localization via Accept-Language header."
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
        description = "Returns venues in a category using code (e.g., 'OPERA', 'MUSEUM'). Localization via Accept-Language header."
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
        description = "Returns all active venue categories for filtering. Localization via Accept-Language header."
    )
    fun listCategories(): ApiResponse<List<VenueCategoryDto>> {
        val language = LocaleHelper.currentLanguage()
        val categories = venueService.listCategories(language)
        return ApiResponse.success(categories, "Categories retrieved successfully")
    }
}