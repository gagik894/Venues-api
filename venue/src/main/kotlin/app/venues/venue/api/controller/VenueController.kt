package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.shared.i18n.LocaleHelper
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.dto.*
import app.venues.venue.api.service.VenueSecurityService
import app.venues.venue.dto.SmtpConfig
import app.venues.venue.service.VenueService
import app.venues.venue.service.VenueSettingsService
import app.venues.venue.service.VenueWebsiteService
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
 * Consolidated controller for venue operations (public read + privileged management).
 * Includes website configuration endpoints (moved from domain-based admin).
 */
@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Venues", description = "Venue public discovery and management")
class VenueController(
    private val venueService: VenueService,
    private val venueSettingsService: VenueSettingsService,
    private val venueSecurityService: VenueSecurityService,
    private val venueWebsiteService: VenueWebsiteService
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
    @Operation(summary = "Get venue by slug", description = "SEO-friendly access. Localization via Accept-Language.")
    fun getVenueBySlug(@PathVariable slug: String): ApiResponse<VenueDetailResponse> {
        val language = LocaleHelper.currentLanguage()
        val venue = venueService.getVenueBySlug(slug, language)
        return ApiResponse.success(venue, "Venue retrieved successfully")
    }

    @GetMapping("/search")
    @Operation(summary = "Search venues", description = "Case-insensitive search by name.")
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
    @Operation(summary = "List venues by city", description = "Filter by city slug.")
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
    @Operation(summary = "List venues by region", description = "Filter by ISO region code.")
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
    @Operation(summary = "List venues by category", description = "Filter by category code.")
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
    @Operation(summary = "List venue categories", description = "All active categories for filtering.")
    fun listCategories(): ApiResponse<List<VenueCategoryDto>> {
        val language = LocaleHelper.currentLanguage()
        val categories = venueService.listCategories(language)
        return ApiResponse.success(categories, "Categories retrieved successfully")
    }

    // Management operations (auth required)

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create venue", description = "Admin-only create; status starts as PENDING_APPROVAL.")
    fun createVenue(@Valid @RequestBody request: CreateVenueRequest): ApiResponse<VenueDetailResponse> {
        val venue = venueService.createVenue(request)
        val detail = venueService.getVenue(venue.id, LocaleHelper.currentLanguage())
        return ApiResponse.success(detail, "Venue created successfully")
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('VENUE_OWNER')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Update venue", description = "Admin/owner only.")
    fun updateVenue(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateVenueRequest
    ): ApiResponse<VenueDetailResponse> {
        val updated = venueService.updateVenue(id, request)
        val detail = venueService.getVenue(updated.id, LocaleHelper.currentLanguage())
        return ApiResponse.success(detail, "Venue updated successfully")
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete venue", description = "Soft delete (status=DELETED). Admin only.")
    fun deleteVenue(@PathVariable id: UUID): ApiResponse<Unit> {
        venueService.deleteVenue(id)
        return ApiResponse.success(Unit, "Venue deleted successfully")
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Activate venue", description = "Sets status ACTIVE.")
    fun activateVenue(@PathVariable id: UUID): ApiResponse<VenueDetailResponse> {
        val venue = venueService.activateVenue(id)
        val detail = venueService.getVenue(venue.id, LocaleHelper.currentLanguage())
        return ApiResponse.success(detail, "Venue activated successfully")
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Suspend venue", description = "Sets status SUSPENDED.")
    fun suspendVenue(@PathVariable id: UUID): ApiResponse<VenueDetailResponse> {
        val venue = venueService.suspendVenue(id)
        val detail = venueService.getVenue(venue.id, LocaleHelper.currentLanguage())
        return ApiResponse.success(detail, "Venue suspended successfully")
    }

    @PutMapping("/{id}/smtp")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Set SMTP config", description = "Configure SMTP; password masked in responses.")
    fun setSmtpConfig(
        @PathVariable id: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody config: SmtpConfig
    ): ApiResponse<SmtpConfigResponse?> {
        venueSecurityService.requireVenueManagementPermission(staffId, id)
        venueSettingsService.updateSmtpConfig(id, config)
        val masked = venueSettingsService.getSmtpConfigMasked(id)
        return ApiResponse.success(SmtpConfigResponse.from(masked), "SMTP configuration updated successfully")
    }

    @GetMapping("/{id}/smtp")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Get SMTP config", description = "Masked password.")
    fun getSmtpConfig(
        @PathVariable id: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<SmtpConfigResponse?> {
        venueSecurityService.requireVenueManagementPermission(staffId, id)
        val config = venueSettingsService.getSmtpConfigMasked(id)
        return ApiResponse.success(SmtpConfigResponse.from(config), "SMTP configuration retrieved")
    }

    @DeleteMapping("/{id}/smtp")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete SMTP config", description = "Removes SMTP settings; global SMTP used.")
    fun deleteSmtpConfig(
        @PathVariable id: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, id)
        venueSettingsService.updateSmtpConfig(id, null)
        return ApiResponse.success(Unit, "SMTP configuration deleted")
    }

    // Website Configuration (moved from domain-based admin)

    @GetMapping("/{id}/website/branding")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Get venue branding",
        description = "Retrieve white-label branding configuration for venue website"
    )
    fun getVenueBranding(
        @PathVariable id: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<VenueBrandingDto> {
        venueSecurityService.requireVenueManagementPermission(staffId, id)
        val branding = venueWebsiteService.getVenueBranding(id)
        return ApiResponse.success(branding, "Branding retrieved successfully")
    }

    @PutMapping("/{id}/website/branding")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Update venue branding",
        description = "Update white-label branding configuration (logo, colors, etc.)"
    )
    fun updateVenueBranding(
        @PathVariable id: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: UpdateVenueBrandingRequest
    ): ApiResponse<VenueBrandingDto> {
        venueSecurityService.requireVenueManagementPermission(staffId, id)
        val branding = venueWebsiteService.updateVenueBranding(id, request)
        return ApiResponse.success(branding, "Branding updated successfully")
    }
}