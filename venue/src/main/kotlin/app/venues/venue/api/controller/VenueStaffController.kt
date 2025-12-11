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
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Staff/Admin controller for venue management operations.
 *
 * Handles all privileged venue operations including:
 * - Venue CRUD (create, update, delete)
 * - Status management (activate, suspend)
 * - SMTP configuration
 * - Website branding configuration
 *
 * All endpoints require STAFF or SUPER_ADMIN authentication.
 */
@RestController
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/v1/staff/venues")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Venue Management", description = "Staff/Admin venue management operations")
class VenueStaffController(
    private val venueService: VenueService,
    private val venueSettingsService: VenueSettingsService,
    private val venueSecurityService: VenueSecurityService,
    private val venueWebsiteService: VenueWebsiteService
) {

    // ===========================================
    // VENUE CRUD
    // ===========================================

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "List all venues (admin)",
        description = "Returns all venues including inactive/pending. Super admin only."
    )
    fun listAllVenues(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<VenueAdminResponse>> {
        val language = LocaleHelper.currentLanguage()
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val venues = venueService.listAllVenues(pageable, language)
        return ApiResponse.success(venues, "Venues retrieved successfully")
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create venue",
        description = "Create a new venue. Status starts as PENDING_APPROVAL. Super admin only."
    )
    fun createVenue(
        @Valid @RequestBody request: CreateVenueRequest
    ): ApiResponse<VenueAdminResponse> {
        val venue = venueService.createVenue(request)
        return ApiResponse.success(venue, "Venue created successfully")
    }

    @GetMapping("/{venueId}")
    @Operation(
        summary = "Get venue details (admin)",
        description = "Get full venue details including admin-only fields."
    )
    fun getVenueAdmin(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<VenueAdminResponse> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)
        val language = LocaleHelper.currentLanguage()
        val venue = venueService.getVenueByIdAdmin(venueId, language)
        return ApiResponse.success(venue, "Venue retrieved successfully")
    }

    @PutMapping("/{venueId}")
    @Operation(
        summary = "Update venue",
        description = "Update venue information. Requires venue management permission."
    )
    fun updateVenue(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: UpdateVenueRequest
    ): ApiResponse<VenueAdminResponse> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        val updated = venueService.updateVenue(venueId, request)
        return ApiResponse.success(updated, "Venue updated successfully")
    }

    @DeleteMapping("/{venueId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete venue",
        description = "Soft delete venue (sets status to DELETED). Super admin only."
    )
    fun deleteVenue(@PathVariable venueId: UUID): ApiResponse<Unit> {
        venueService.deleteVenue(venueId)
        return ApiResponse.success(Unit, "Venue deleted successfully")
    }

    // ===========================================
    // STATUS MANAGEMENT
    // ===========================================

    @PostMapping("/{venueId}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Activate venue",
        description = "Sets venue status to ACTIVE. Super admin only."
    )
    fun activateVenue(@PathVariable venueId: UUID): ApiResponse<VenueDetailResponse> {
        val venue = venueService.activateVenue(venueId)
        val detail = venueService.getVenue(venue.id, LocaleHelper.currentLanguage())
        return ApiResponse.success(detail, "Venue activated successfully")
    }

    @PostMapping("/{venueId}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Suspend venue",
        description = "Sets venue status to SUSPENDED. Super admin only."
    )
    fun suspendVenue(@PathVariable venueId: UUID): ApiResponse<VenueDetailResponse> {
        val venue = venueService.suspendVenue(venueId)
        val detail = venueService.getVenue(venue.id, LocaleHelper.currentLanguage())
        return ApiResponse.success(detail, "Venue suspended successfully")
    }

    // ===========================================
    // SMTP CONFIGURATION
    // ===========================================

    @GetMapping("/{venueId}/smtp")
    @Operation(
        summary = "Get SMTP configuration",
        description = "Retrieve SMTP settings for venue. Password is masked."
    )
    fun getSmtpConfig(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<SmtpConfigResponse?> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        val config = venueSettingsService.getSmtpConfigMasked(venueId)
        return ApiResponse.success(
            config?.let { SmtpConfigResponse.from(it) },
            if (config != null) "SMTP configuration retrieved" else "No SMTP configuration found"
        )
    }

    @PutMapping("/{venueId}/smtp")
    @Operation(
        summary = "Set SMTP configuration",
        description = "Configure SMTP settings for venue email notifications. Credentials encrypted at rest."
    )
    fun setSmtpConfig(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody config: SmtpConfig
    ): ApiResponse<SmtpConfigResponse?> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        venueSettingsService.updateSmtpConfig(venueId, config)
        val masked = venueSettingsService.getSmtpConfigMasked(venueId)
        return ApiResponse.success(SmtpConfigResponse.from(masked), "SMTP configuration updated successfully")
    }

    @DeleteMapping("/{venueId}/smtp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete SMTP configuration",
        description = "Remove SMTP settings. Venue will use global SMTP for emails."
    )
    fun deleteSmtpConfig(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        venueSettingsService.updateSmtpConfig(venueId, null)
        return ApiResponse.success(Unit, "SMTP configuration deleted")
    }

    // ===========================================
    // WEBSITE BRANDING
    // ===========================================

    @GetMapping("/{venueId}/branding")
    @Operation(
        summary = "Get venue branding",
        description = "Retrieve white-label branding configuration (logo, colors, hero, etc.)"
    )
    fun getVenueBranding(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<VenueBrandingDto> {
        venueSecurityService.requireVenueViewPermission(staffId, venueId)
        val branding = venueWebsiteService.getVenueBranding(venueId)
        return ApiResponse.success(branding, "Branding retrieved successfully")
    }

    @PutMapping("/{venueId}/branding")
    @Operation(
        summary = "Update venue branding",
        description = "Update white-label branding configuration (colors, hero, about blocks, contact config)."
    )
    fun updateVenueBranding(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: UpdateVenueBrandingRequest
    ): ApiResponse<VenueBrandingDto> {
        venueSecurityService.requireVenueEditPermission(staffId, venueId)
        val branding = venueWebsiteService.updateVenueBranding(venueId, request)
        return ApiResponse.success(branding, "Branding updated successfully")
    }
}
