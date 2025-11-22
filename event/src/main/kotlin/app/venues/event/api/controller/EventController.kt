package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.event.api.dto.EventResponse
import app.venues.event.api.dto.EventSessionResponse
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.Event
import app.venues.event.service.EventService
import app.venues.seating.api.SeatingApi
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
     */
    @GetMapping
    @Operation(
        summary = "Get all events",
        description = "Browse all publicly visible upcoming events. Use 'lang' parameter for translations (e.g., ?lang=hy for Armenian)"
    )
    fun getAllEvents(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDirection: String?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Fetching all events, language: $lang" }

        val allowedSortFields = setOf("createdAt", "title", "id")
        val pageable = PageableMapper.createPageable(limit, offset, sortBy, sortDirection, allowedSortFields)

        val events = eventService.getAllEvents(pageable)

        return ApiResponse.success(
            data = mapEventsWithVenueNames(events, lang),
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get event by ID.
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get event details",
        description = "Get detailed information about a specific event. Use 'lang' parameter for translations (e.g., ?lang=hy for Armenian)"
    )
    fun getEventById(
        @PathVariable id: UUID,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<EventResponse> {
        logger.debug { "Fetching event: $id, language: $lang" }

        val event = eventService.getEventById(id)
        val venueName = venueApi.getVenueNameTranslated(event.venueId, lang) ?: "Unknown"
        val seatingChartName = event.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return ApiResponse.success(
            data = eventMapper.toResponse(event, venueName, seatingChartName, includeStats = true, language = lang),
            message = "Event retrieved successfully"
        )
    }

    /**
     * Search events by title.
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search events",
        description = "Search events by title (case-insensitive). Use 'lang' parameter for translations"
    )
    fun searchEvents(
        @RequestParam("q") searchTerm: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Searching events: $searchTerm, language: $lang" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.searchEvents(searchTerm, pageable)

        return ApiResponse.success(
            data = mapEventsWithVenueNames(events, lang),
            message = "Search completed successfully"
        )
    }

    /**
     * Get events by venue.
     */
    @GetMapping("/venue/{venueId}")
    @Operation(
        summary = "Get events by venue",
        description = "Get all events for a specific venue. Use 'lang' parameter for translations"
    )
    fun getEventsByVenue(
        @PathVariable venueId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Fetching events for venue: $venueId, language: $lang" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.getEventsByVenue(venueId, pageable)

        return ApiResponse.success(
            data = mapEventsWithVenueNames(events, lang),
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get events by category.
     */
    @GetMapping("/category/{categoryId}")
    @Operation(
        summary = "Get events by category",
        description = "Get all events in a specific category. Use 'lang' parameter for translations"
    )
    fun getEventsByCategory(
        @PathVariable categoryId: Long,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Fetching events for category: $categoryId, language: $lang" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.getEventsByCategory(categoryId, pageable)

        return ApiResponse.success(
            data = mapEventsWithVenueNames(events, lang),
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get events by tag.
     */
    @GetMapping("/tag/{tag}")
    @Operation(
        summary = "Get events by tag",
        description = "Get all events with a specific tag. Use 'lang' parameter for translations"
    )
    fun getEventsByTag(
        @PathVariable tag: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Fetching events for tag: $tag, language: $lang" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.getEventsByTag(tag, pageable)

        return ApiResponse.success(
            data = mapEventsWithVenueNames(events, lang),
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
            data = sessions.map { eventMapper.toSessionResponse(it) },
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
            data = sessions.map { eventMapper.toSessionResponse(it) },
            message = "Bookable sessions retrieved successfully"
        )
    }

    private fun mapEventsWithVenueNames(
        eventsPage: Page<Event>,
        language: String?
    ): Page<EventResponse> {
        val venueIds = eventsPage.content.map { it.venueId }.toSet()
        val venueNamesMap = venueApi.getVenueNamesBatch(venueIds, language)

        return eventsPage.map { event ->
            val venueName = venueNamesMap[event.venueId] ?: "Unknown"
            val seatingChartName = event.seatingChartId?.let { seatingApi.getSeatingChartName(it) }
            eventMapper.toResponse(event, venueName, seatingChartName, includeStats = true, language = language)
        }
    }
}

