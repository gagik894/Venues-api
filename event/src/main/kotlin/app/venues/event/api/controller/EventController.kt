package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.common.util.PaginationUtil
import app.venues.event.api.dto.EventResponse
import app.venues.event.api.dto.EventSessionResponse
import app.venues.event.api.dto.EventTranslationResponse
import app.venues.event.service.EventService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.*

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
    private val eventService: EventService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get all publicly visible events.
     */
    @GetMapping
    @Operation(
        summary = "Get all events",
        description = "Browse all publicly visible upcoming events"
    )
    fun getAllEvents(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDirection: String?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Fetching all events" }

        val allowedSortFields = setOf("createdAt", "title", "id")
        val pageable = PaginationUtil.createPageable(limit, offset, sortBy, sortDirection, allowedSortFields)

        val events = eventService.getAllEvents(pageable)

        return ApiResponse.success(
            data = events,
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get event by ID.
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get event details",
        description = "Get detailed information about a specific event"
    )
    fun getEventById(@PathVariable id: Long): ApiResponse<EventResponse> {
        logger.debug { "Fetching event: $id" }

        val event = eventService.getEventById(id, includeStats = true)

        return ApiResponse.success(
            data = event,
            message = "Event retrieved successfully"
        )
    }

    /**
     * Search events by title.
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search events",
        description = "Search events by title (case-insensitive)"
    )
    fun searchEvents(
        @RequestParam("q") searchTerm: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Searching events: $searchTerm" }

        val pageable = PaginationUtil.createPageable(limit, offset)
        val events = eventService.searchEvents(searchTerm, pageable)

        return ApiResponse.success(
            data = events,
            message = "Search completed successfully"
        )
    }

    /**
     * Get events by venue.
     */
    @GetMapping("/venue/{venueId}")
    @Operation(
        summary = "Get events by venue",
        description = "Get all events for a specific venue"
    )
    fun getEventsByVenue(
        @PathVariable venueId: Long,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Fetching events for venue: $venueId" }

        val pageable = PaginationUtil.createPageable(limit, offset)
        val events = eventService.getEventsByVenue(venueId, pageable)

        return ApiResponse.success(
            data = events,
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get events by category.
     */
    @GetMapping("/category/{categoryId}")
    @Operation(
        summary = "Get events by category",
        description = "Get all events in a specific category"
    )
    fun getEventsByCategory(
        @PathVariable categoryId: Long,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Fetching events for category: $categoryId" }

        val pageable = PaginationUtil.createPageable(limit, offset)
        val events = eventService.getEventsByCategory(categoryId, pageable)

        return ApiResponse.success(
            data = events,
            message = "Events retrieved successfully"
        )
    }

    /**
     * Get events by tag.
     */
    @GetMapping("/tag/{tag}")
    @Operation(
        summary = "Get events by tag",
        description = "Get all events with a specific tag"
    )
    fun getEventsByTag(
        @PathVariable tag: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventResponse>> {
        logger.debug { "Fetching events for tag: $tag" }

        val pageable = PaginationUtil.createPageable(limit, offset)
        val events = eventService.getEventsByTag(tag, pageable)

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
        @PathVariable id: Long,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<EventSessionResponse>> {
        logger.debug { "Fetching sessions for event: $id" }

        val pageable = PaginationUtil.createPageable(limit, offset)
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
    fun getBookableSessions(@PathVariable id: Long): ApiResponse<List<EventSessionResponse>> {
        logger.debug { "Fetching bookable sessions for event: $id" }

        val sessions = eventService.getBookableSessions(id)

        return ApiResponse.success(
            data = sessions,
            message = "Bookable sessions retrieved successfully"
        )
    }

    /**
     * Get translations for an event.
     */
    @GetMapping("/{id}/translations")
    @Operation(
        summary = "Get event translations",
        description = "Get all translations for an event"
    )
    fun getTranslations(@PathVariable id: Long): ApiResponse<List<EventTranslationResponse>> {
        logger.debug { "Fetching translations for event: $id" }

        val translations = eventService.getTranslations(id)

        return ApiResponse.success(
            data = translations,
            message = "Translations retrieved successfully"
        )
    }
}

