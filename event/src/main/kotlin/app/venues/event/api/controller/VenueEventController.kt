package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.event.api.dto.*
import app.venues.event.service.EventService
import app.venues.venue.api.service.VenueSecurityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Controller for venue owner event management.
 *
 * Provides endpoints for venue owners to:
 * - Create events
 * - Update events
 * - Delete events
 * - Manage sessions
 * - Manage translations
 *
 * Uses StaffSecurityFacade for permission checking.
 */
@RestController
@RequestMapping("/api/v1/venue/events")
@Tag(name = "Venue Events", description = "Event management for venue owners")
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
class VenueEventController(
    private val eventService: EventService,
    private val venueSecurityService: VenueSecurityService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Create event for authenticated venue.
     */
    @PostMapping
    @Operation(
        summary = "Create event",
        description = "Create a new event for your venue (Venue owners only)"
    )
    fun createEvent(
        @Valid @RequestBody request: EventRequest,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<EventResponse> {
        val venueId = request.venueId

        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        logger.debug { "Creating event for venue: $venueId by staff: $staffId" }

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
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: EventRequest,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<EventResponse> {
        // Fetch event to get venueId for permission check
        val existingEvent = eventService.getEventById(eventId)
        venueSecurityService.requireVenueManagementPermission(staffId, existingEvent.venueId)

        logger.debug { "Updating event: $eventId for venue: ${existingEvent.venueId} by staff: $staffId" }

        val event = eventService.updateEvent(eventId, existingEvent.venueId, request)

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
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<Unit> {
        val existingEvent = eventService.getEventById(eventId)
        venueSecurityService.requireVenueManagementPermission(staffId, existingEvent.venueId)

        logger.debug { "Deleting event: $eventId for venue: ${existingEvent.venueId} by staff: $staffId" }

        eventService.deleteEvent(eventId, existingEvent.venueId)

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
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: EventSessionRequest,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<EventSessionResponse> {
        val existingEvent = eventService.getEventById(eventId)
        venueSecurityService.requireVenueManagementPermission(staffId, existingEvent.venueId)

        logger.debug { "Adding session to event: $eventId for venue: ${existingEvent.venueId} by staff: $staffId" }

        val session = eventService.addSession(eventId, existingEvent.venueId, request)

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
        @PathVariable eventId: UUID,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: EventSessionRequest,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<EventSessionResponse> {
        // We use the eventId from path to check permission on the venue
        val existingEvent = eventService.getEventById(eventId)
        venueSecurityService.requireVenueManagementPermission(staffId, existingEvent.venueId)

        logger.debug { "Updating session: $sessionId for venue: ${existingEvent.venueId} by staff: $staffId" }

        val session = eventService.updateSession(sessionId, existingEvent.venueId, request)

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
        @PathVariable eventId: UUID,
        @PathVariable sessionId: UUID,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<Unit> {
        val existingEvent = eventService.getEventById(eventId)
        venueSecurityService.requireVenueManagementPermission(staffId, existingEvent.venueId)

        logger.debug { "Deleting session: $sessionId for venue: ${existingEvent.venueId} by staff: $staffId" }

        eventService.deleteSession(sessionId, existingEvent.venueId)

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
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID,
        @Valid @RequestBody request: EventTranslationRequest
    ): ApiResponse<EventTranslationResponse> {
        val existingEvent = eventService.getEventById(eventId)
        venueSecurityService.requireVenueManagementPermission(staffId, existingEvent.venueId)

        logger.debug { "Setting translation for event: $eventId for venue: ${existingEvent.venueId} by staff: $staffId" }

        val translation = eventService.setTranslation(eventId, existingEvent.venueId, request)

        return ApiResponse.success(
            data = translation,
            message = "Translation set successfully"
        )
    }
}

