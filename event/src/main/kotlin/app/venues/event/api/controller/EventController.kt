package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.event.api.dto.EventResponse
import app.venues.event.api.dto.EventSessionResponse
import app.venues.event.api.dto.EventSummaryResponse
import app.venues.event.api.mapper.EventMapper
import app.venues.event.service.EventService
import app.venues.seating.api.SeatingApi
import app.venues.shared.i18n.LocaleHelper
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Public controller for event operations.
 *
 * Provides endpoints for:
 * - Browsing events
 * - Searching events
 * - Viewing event details
 * - Getting event sessions
 */
@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Public event browsing and search")
class EventController(
    private val eventService: EventService,
    private val eventMapper: EventMapper,
    private val venueApi: VenueApi,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get all publicly visible events.
     *
     * Language is resolved from Accept-Language header.
     */
    @GetMapping
    @Operation(
        summary = "Get all events",
        description = "Browse all publicly visible upcoming events. Use Accept-Language header for translations (e.g., 'hy' for Armenian)"
    )
    fun getAllEvents(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDirection: String?
    ): ApiResponse<Page<EventSummaryResponse>> {
        val language = LocaleHelper.currentLanguage()
        logger.debug { "Fetching all events, language: $language" }

        val allowedSortFields = setOf("createdAt", "title", "id", "firstSessionStart")
        val pageable = PageableMapper.createPageable(limit, offset, sortBy, sortDirection, allowedSortFields)

        val events = eventService.getAllEventSummaries(pageable, language)

        return ApiResponse.success(
            data = events,
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get event by ID.
     *
     * Language is resolved from Accept-Language header.
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get event details",
        description = "Get detailed information about a specific event. Use Accept-Language header for translations"
    )
    fun getEventById(@PathVariable id: UUID): ApiResponse<EventResponse> {
        val language = LocaleHelper.currentLanguage()
        logger.debug { "Fetching event: $id, language: $language" }

        val event = eventService.getPublishedEventById(id)
        val venueName = venueApi.getVenueNameTranslated(event.venueId, language) ?: "Unknown"
        val seatingChartName = event.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return ApiResponse.success(
            data = eventMapper.toResponse(event, venueName, seatingChartName, includeStats = true, language = language),
            message = "Event retrieved successfully"
        )
    }

    /**
     * Search events by title.
     *
     * Language is resolved from Accept-Language header.
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search events",
        description = "Search events by title (case-insensitive). Use Accept-Language header for translations"
    )
    fun searchEvents(
        @RequestParam("q") searchTerm: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventSummaryResponse>> {
        val language = LocaleHelper.currentLanguage()
        logger.debug { "Searching events: $searchTerm, language: $language" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.searchEventSummaries(searchTerm, pageable, language)

        return ApiResponse.success(
            data = events,
            message = "Search completed successfully"
        )
    }

    /**
     * Get events by venue.
     *
     * Language is resolved from Accept-Language header.
     */
    @GetMapping("/venue/{venueId}")
    @Operation(
        summary = "Get events by venue",
        description = "Get all events for a specific venue. Use Accept-Language header for translations"
    )
    fun getEventsByVenue(
        @PathVariable venueId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventSummaryResponse>> {
        val language = LocaleHelper.currentLanguage()
        logger.debug { "Fetching events for venue: $venueId, language: $language" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.getEventSummariesByVenue(venueId, pageable, language)

        return ApiResponse.success(
            data = events,
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get events by category.
     *
     * Language is resolved from Accept-Language header.
     */
    @GetMapping("/category/{categoryId}")
    @Operation(
        summary = "Get events by category",
        description = "Get all events in a specific category. Use Accept-Language header for translations"
    )
    fun getEventsByCategory(
        @PathVariable categoryId: Long,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventSummaryResponse>> {
        val language = LocaleHelper.currentLanguage()
        logger.debug { "Fetching events for category: $categoryId, language: $language" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.getEventSummariesByCategory(categoryId, pageable, language)

        return ApiResponse.success(
            data = events,
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get events by tag.
     *
     * Language is resolved from Accept-Language header.
     */
    @GetMapping("/tag/{tag}")
    @Operation(
        summary = "Get events by tag",
        description = "Get all events with a specific tag. Use Accept-Language header for translations"
    )
    fun getEventsByTag(
        @PathVariable tag: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventSummaryResponse>> {
        val language = LocaleHelper.currentLanguage()
        logger.debug { "Fetching events for tag: $tag, language: $language" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.getEventSummariesByTag(tag, pageable, language)

        return ApiResponse.success(
            data = events,
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get sessions for an event.
     */
    @GetMapping("/{id}/sessions")
    @Operation(
        summary = "Get event sessions",
        description = "Get all sessions (time slots) for an event"
    )
    fun getEventSessions(
        @PathVariable id: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventSessionResponse>> {
        logger.debug { "Fetching sessions for event: $id" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val sessions = eventService.getEventSessions(id, pageable)

        return ApiResponse.success(
            data = sessions,
            message = "Sessions retrieved successfully"
        )
    }

    /**
     * Get bookable sessions for an event.
     */
    @GetMapping("/{id}/sessions/bookable")
    @Operation(
        summary = "Get bookable sessions",
        description = "Get all bookable sessions for an event (upcoming with available tickets)"
    )
    fun getBookableSessions(@PathVariable id: UUID): ApiResponse<List<EventSessionResponse>> {
        logger.debug { "Fetching bookable sessions for event: $id" }

        val sessions = eventService.getBookableSessions(id)

        return ApiResponse.success(
            data = sessions,
            message = "Bookable sessions retrieved successfully"
        )
    }
}

