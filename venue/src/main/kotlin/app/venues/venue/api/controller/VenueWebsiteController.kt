package app.venues.venue.api.controller

import app.venues.common.model.ApiResponse
import app.venues.venue.api.dto.AboutPageDto
import app.venues.venue.api.dto.ContactPageDto
import app.venues.venue.api.dto.HomePageDto
import app.venues.venue.api.dto.WebsiteLayoutDto
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

    @GetMapping("/layout")
    @Operation(summary = "Get website layout", description = "Returns header, footer, and theme configuration.")
    fun getLayout(
        @PathVariable venueId: UUID,
        @RequestParam(defaultValue = "en") lang: String
    ): ApiResponse<WebsiteLayoutDto> {
        val data = venueWebsiteService.getWebsiteLayout(venueId, lang)
        return ApiResponse.success(data, "Layout retrieved successfully")
    }

    @GetMapping("/pages/home")
    @Operation(summary = "Get home page content")
    fun getHomePage(
        @PathVariable venueId: UUID,
        @RequestParam(defaultValue = "en") lang: String
    ): ApiResponse<HomePageDto> {
        val data = venueWebsiteService.getHomePage(venueId, lang)
        return ApiResponse.success(data, "Home page retrieved successfully")
    }

    @GetMapping("/pages/about")
    @Operation(summary = "Get about page content")
    fun getAboutPage(
        @PathVariable venueId: UUID,
        @RequestParam(defaultValue = "en") lang: String
    ): ApiResponse<AboutPageDto> {
        val data = venueWebsiteService.getAboutPage(venueId, lang)
        return ApiResponse.success(data, "About page retrieved successfully")
    }

    @GetMapping("/pages/contact")
    @Operation(summary = "Get contact page content")
    fun getContactPage(
        @PathVariable venueId: UUID,
        @RequestParam(defaultValue = "en") lang: String
    ): ApiResponse<ContactPageDto> {
        val data = venueWebsiteService.getContactPage(venueId, lang)
        return ApiResponse.success(data, "Contact page retrieved successfully")
    }
}