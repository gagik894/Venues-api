package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.event.api.dto.*
import app.venues.event.service.EventService
import app.venues.shared.security.util.SecurityUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Controller for venue owner event management.
 *
 * Provides endpoints for venue owners to:
 * - Create events
 * - Update events
 * - Delete events
 * - Manage sessions
 * - Manage translations
 */
@RestController
@RequestMapping("/api/v1/venues/{venueId}/events")
@Tag(name = "Venue Events", description = "Event management for venue owners")
@PreAuthorize("hasRole('VENUE')")
class VenueEventController(
    private val eventService: EventService,
    private val securityUtil: SecurityUtil
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Create event for venue.
     */
    @PostMapping
    @Operation(
        summary = "Create event",
        description = "Create a new event for the venue (Venue owners only)"
    )
    fun createEvent(
        @PathVariable venueId: Long,
        @Valid @RequestBody request: EventRequest
    ): ApiResponse<EventResponse> {
        logger.debug { "Creating event for venue: $venueId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val event = eventService.createEvent(venueId, request)

        return ApiResponse.success(
            data = event,
            message = "Event created successfully"
        )
    }

    /**
     * Update event.
     */
    @PutMapping("/{eventId}")
    @Operation(
        summary = "Update event",
        description = "Update event details (Venue owners only)"
    )
    fun updateEvent(
        @PathVariable venueId: Long,
        @PathVariable eventId: Long,
        @Valid @RequestBody request: EventRequest
    ): ApiResponse<EventResponse> {
        logger.debug { "Updating event: $eventId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val event = eventService.updateEvent(eventId, venueId, request)

        return ApiResponse.success(
            data = event,
            message = "Event updated successfully"
        )
    }

    /**
     * Delete event.
     */
    @DeleteMapping("/{eventId}")
    @Operation(
        summary = "Delete event",
        description = "Delete an event (Venue owners only)"
    )
    fun deleteEvent(
        @PathVariable venueId: Long,
        @PathVariable eventId: Long
    ): ApiResponse<Unit> {
        logger.debug { "Deleting event: $eventId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        eventService.deleteEvent(eventId, venueId)

        return ApiResponse.success(
            data = Unit,
            message = "Event deleted successfully"
        )
    }

    // ===========================================
    // SESSION MANAGEMENT
    // ===========================================

    /**
     * Add session to event.
     */
    @PostMapping("/{eventId}/sessions")
    @Operation(
        summary = "Add session",
        description = "Add a new session to an event (Venue owners only)"
    )
    fun addSession(
        @PathVariable venueId: Long,
        @PathVariable eventId: Long,
        @Valid @RequestBody request: EventSessionRequest
    ): ApiResponse<EventSessionResponse> {
        logger.debug { "Adding session to event: $eventId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val session = eventService.addSession(eventId, request)

        return ApiResponse.success(
            data = session,
            message = "Session added successfully"
        )
    }

    /**
     * Update session.
     */
    @PutMapping("/{eventId}/sessions/{sessionId}")
    @Operation(
        summary = "Update session",
        description = "Update session details (Venue owners only)"
    )
    fun updateSession(
        @PathVariable venueId: Long,
        @PathVariable eventId: Long,
        @PathVariable sessionId: Long,
        @Valid @RequestBody request: EventSessionRequest
    ): ApiResponse<EventSessionResponse> {
        logger.debug { "Updating session: $sessionId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val session = eventService.updateSession(sessionId, request)

        return ApiResponse.success(
            data = session,
            message = "Session updated successfully"
        )
    }

    /**
     * Delete session.
     */
    @DeleteMapping("/{eventId}/sessions/{sessionId}")
    @Operation(
        summary = "Delete session",
        description = "Delete a session (Venue owners only)"
    )
    fun deleteSession(
        @PathVariable venueId: Long,
        @PathVariable eventId: Long,
        @PathVariable sessionId: Long
    ): ApiResponse<Unit> {
        logger.debug { "Deleting session: $sessionId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        eventService.deleteSession(sessionId)

        return ApiResponse.success(
            data = Unit,
            message = "Session deleted successfully"
        )
    }

    // ===========================================
    // TRANSLATION MANAGEMENT
    // ===========================================

    /**
     * Set translation for event.
     */
    @PutMapping("/{eventId}/translations")
    @Operation(
        summary = "Set translation",
        description = "Add or update translation for an event (Venue owners only)"
    )
    fun setTranslation(
        @PathVariable venueId: Long,
        @PathVariable eventId: Long,
        @Valid @RequestBody request: EventTranslationRequest
    ): ApiResponse<EventTranslationResponse> {
        logger.debug { "Setting translation for event: $eventId" }

        // Verify venue ownership
        securityUtil.requireVenueOwnership(venueId)

        val translation = eventService.setTranslation(eventId, request)

        return ApiResponse.success(
            data = translation,
            message = "Translation set successfully"
        )
    }
}

