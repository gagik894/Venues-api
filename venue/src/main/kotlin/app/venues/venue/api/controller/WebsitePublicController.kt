package app.venues.venue.api.controller

import app.venues.common.exception.VenuesException
import app.venues.common.model.ApiResponse
import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSummaryDto
import app.venues.shared.i18n.LocaleHelper
import app.venues.shared.web.context.DomainContext
import app.venues.venue.api.dto.AboutPageDto
import app.venues.venue.api.dto.ContactPageDto
import app.venues.venue.api.dto.HomePageDto
import app.venues.venue.api.dto.WebsiteLayoutDto
import app.venues.venue.service.VenueWebsiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Public white-label website endpoints resolved by X-Venue-Domain header.
 */
@RestController
@RequestMapping("/api/v1/site")
@Tag(name = "Site", description = "White-label public website endpoints (domain header based)")
class WebsitePublicController(
    private val venueWebsiteService: VenueWebsiteService,
    private val eventApi: EventApi
) {

    private fun requireVenueId() = DomainContext.get()?.venueId
        ?: throw VenuesException.ValidationFailure(
            message = "X-Venue-Domain header required",
            errorCode = "DOMAIN_REQUIRED"
        )

    @GetMapping("/layout")
    @Operation(summary = "Get website layout", description = "Header/footer/theme for white-label site.")
    fun getLayout(): ApiResponse<WebsiteLayoutDto> {
        val venueId = requireVenueId()
        val language = LocaleHelper.currentLanguage()
        val data = venueWebsiteService.getWebsiteLayout(venueId, language)
        return ApiResponse.success(data, "Layout retrieved successfully")
    }

    @GetMapping("/pages/home")
    @Operation(summary = "Get home page content", description = "Domain header required.")
    fun getHomePage(): ApiResponse<HomePageDto> {
        val venueId = requireVenueId()
        val language = LocaleHelper.currentLanguage()
        val data = venueWebsiteService.getHomePage(venueId, language)
        return ApiResponse.success(data, "Home page retrieved successfully")
    }

    @GetMapping("/pages/about")
    @Operation(summary = "Get about page content", description = "Domain header required.")
    fun getAboutPage(): ApiResponse<AboutPageDto> {
        val venueId = requireVenueId()
        val language = LocaleHelper.currentLanguage()
        val data = venueWebsiteService.getAboutPage(venueId, language)
        return ApiResponse.success(data, "About page retrieved successfully")
    }

    @GetMapping("/pages/contact")
    @Operation(summary = "Get contact page content", description = "Domain header required.")
    fun getContactPage(): ApiResponse<ContactPageDto> {
        val venueId = requireVenueId()
        val language = LocaleHelper.currentLanguage()
        val data = venueWebsiteService.getContactPage(venueId, language)
        return ApiResponse.success(data, "Contact page retrieved successfully")
    }

    @GetMapping("/events")
    @Operation(summary = "List events for venue", description = "Domain header required; optimized for ISR.")
    fun getEvents(
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int
    ): ApiResponse<List<EventSummaryDto>> {
        val venueId = requireVenueId()
        val language = LocaleHelper.currentLanguage()
        val events = eventApi.getEventsByVenue(venueId, language, limit, offset)
        return ApiResponse.success(events, "Events retrieved successfully")
    }
}
