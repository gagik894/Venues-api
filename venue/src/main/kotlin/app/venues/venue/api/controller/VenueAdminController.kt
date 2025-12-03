package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.shared.i18n.LocaleHelper
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.dto.CreateVenueRequest
import app.venues.venue.api.dto.UpdateVenueRequest
import app.venues.venue.api.dto.VenueAdminResponse
import app.venues.venue.api.service.VenueSecurityService
import app.venues.venue.dto.SmtpConfig
import app.venues.venue.service.VenueService
import app.venues.venue.service.VenueSettingsService
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
 * REST controller for venue administration.
 * Requires ADMIN or STAFF role with venue permission.
 */
@RestController
@RequestMapping("/api/v1/admin/venues")
@Tag(name = "Venue Administration", description = "Admin-only venue management endpoints")
class VenueAdminController(
    private val venueService: VenueService,
    private val venueSettingsService: VenueSettingsService,
    private val venueSecurityService: VenueSecurityService
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
        description = "Returns all venues including non-active (admin only). Localization via Accept-Language header."
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

    // ===========================================
    // SMTP CONFIGURATION
    // ===========================================

    @PutMapping("/{id}/smtp")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Set SMTP configuration",
        description = "Configure SMTP settings for venue email notifications. " +
                "Credentials are encrypted at rest."
    )
    fun setSmtpConfig(
        @PathVariable id: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody config: SmtpConfig
    ): ApiResponse<SmtpConfigResponse?> {
        venueSecurityService.requireVenueManagementPermission(staffId, id)
        venueSettingsService.updateSmtpConfig(id, config)
        val masked = venueSettingsService.getSmtpConfigMasked(id)
        return ApiResponse.success(
            SmtpConfigResponse.from(masked),
            "SMTP configuration updated successfully"
        )
    }

    @GetMapping("/{id}/smtp")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "Get SMTP configuration",
        description = "Retrieve SMTP settings for venue. Password is masked for security."
    )
    fun getSmtpConfig(
        @PathVariable id: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<SmtpConfigResponse?> {
        venueSecurityService.requireVenueManagementPermission(staffId, id)
        val config = venueSettingsService.getSmtpConfigMasked(id)
        return ApiResponse.success(
            config?.let { SmtpConfigResponse.from(it) },
            if (config != null) "SMTP configuration retrieved" else "No SMTP configuration found"
        )
    }

    @DeleteMapping("/{id}/smtp")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete SMTP configuration",
        description = "Remove SMTP settings for venue. Venue will use global SMTP for emails."
    )
    fun deleteSmtpConfig(
        @PathVariable id: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, id)
        venueSettingsService.updateSmtpConfig(id, null)
        return ApiResponse.success(Unit, "SMTP configuration deleted")
    }
}

/**
 * Response DTO for SMTP config with masked password.
 */
data class SmtpConfigResponse(
    val email: String,
    val password: String,  // Masked
    val host: String,
    val port: Int,
    val tls: Boolean
) {
    companion object {
        fun from(config: SmtpConfig?): SmtpConfigResponse? {
            return config?.let {
                SmtpConfigResponse(
                    email = it.email,
                    password = it.password,
                    host = it.host,
                    port = it.port,
                    tls = it.tls
                )
            }
        }
    }
}
