package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.venue.api.dto.LocalizedVenueWebsiteDto
import app.venues.venue.api.dto.VenueBrandingDto
import app.venues.venue.service.VenueWebsiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/website")
@Tag(name = "Venue Website", description = "White-label website configuration")
class VenueWebsiteController(
    private val venueWebsiteService: VenueWebsiteService
) {

    @GetMapping("/branding")
    @Operation(
        summary = "Get venue branding",
        description = "Returns branding configuration for the venue's white-label site."
    )
    fun getVenueBranding(@PathVariable venueId: UUID): ApiResponse<VenueBrandingDto> {
        val branding = venueWebsiteService.getVenueBranding(venueId)
        return ApiResponse.success(branding, "Branding retrieved successfully")
    }

    @GetMapping("/data")
    @Operation(
        summary = "Get full website data",
        description = "Returns aggregated data for the venue's white-label site. Supports localization via 'lang' parameter."
    )
    fun getVenueWebsiteData(
        @PathVariable venueId: UUID,
        @RequestParam(defaultValue = "en") lang: String
    ): ApiResponse<LocalizedVenueWebsiteDto> {
        val data = venueWebsiteService.getLocalizedVenueWebsiteData(venueId, lang)
        return ApiResponse.success(data, "Website data retrieved successfully")
    }
}