package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.dto.*
import app.venues.venue.service.VenueService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
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

/**
 * REST controller for venue administration.
 * Requires ADMIN or VENUE_OWNER role. Can use UUIDs internally.
 */
@RestController
@RequestMapping("/api/v1/admin/venues")
@Tag(name = "Venue Administration", description = "Admin-only venue management endpoints")
class VenueAdminController(
    private val venueService: VenueService
) {

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create venue",
        description = "Creates a new venue (admin only). Status will be PENDING_APPROVAL."
    )
    fun createVenue(
        @Valid @RequestBody request: CreateVenueRequest
    ): ApiResponse<VenueAdminResponse> {
        val venue = venueService.createVenue(request)
        return ApiResponse.success(venue, "Venue created successfully")
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('VENUE_OWNER')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Update venue",
        description = "Updates venue information (admin or owner only)."
    )
    fun updateVenue(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateVenueRequest
    ): ApiResponse<VenueAdminResponse> {
        val venue = venueService.updateVenue(id, request)
        return ApiResponse.success(venue, "Venue updated successfully")
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "List all venues",
        description = "Returns all venues including non-active (admin only)."
    )
    fun listAllVenues(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false, defaultValue = "en") lang: String
    ): ApiResponse<Page<VenueAdminResponse>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.listAllVenues(pageable, lang)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Activate venue",
        description = "Sets venue status to ACTIVE (admin only)."
    )
    fun activateVenue(
        @PathVariable id: UUID
    ): ApiResponse<VenueAdminResponse> {
        val venue = venueService.activateVenue(id)
        return ApiResponse.success(venue, "Venue activated successfully")
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Suspend venue",
        description = "Sets venue status to SUSPENDED (admin only)."
    )
    fun suspendVenue(
        @PathVariable id: UUID
    ): ApiResponse<VenueAdminResponse> {
        val venue = venueService.suspendVenue(id)
        return ApiResponse.success(venue, "Venue suspended successfully")
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete venue",
        description = "Soft-deletes venue (admin only). Sets status to DELETED."
    )
    fun deleteVenue(
        @PathVariable id: UUID
    ): ApiResponse<Unit> {
        venueService.deleteVenue(id)
        return ApiResponse.success(Unit, "Venue deleted successfully")
    }
}

