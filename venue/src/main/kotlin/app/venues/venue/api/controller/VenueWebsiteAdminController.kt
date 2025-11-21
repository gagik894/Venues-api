package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.venue.api.dto.UpdateVenueBrandingRequest
import app.venues.venue.api.dto.VenueBrandingDto
import app.venues.venue.service.VenueSecurityService
import app.venues.venue.service.VenueWebsiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/admin/venues/{venueId}/website")
@Tag(name = "Venue Website Administration", description = "Manage white-label website configuration")
class VenueWebsiteAdminController(
    private val venueWebsiteService: VenueWebsiteService,
    private val venueSecurityService: VenueSecurityService
) {

    @PutMapping("/branding")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STAFF')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Update venue branding", description = "Updates branding configuration.")
    fun updateVenueBranding(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestBody request: UpdateVenueBrandingRequest
    ): ApiResponse<VenueBrandingDto> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val branding = venueWebsiteService.updateVenueBranding(venueId, request)
        return ApiResponse.success(branding, "Branding updated successfully")
    }
}
