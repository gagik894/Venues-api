package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.event.api.dto.*
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.EventStatus
import app.venues.event.domain.SessionStatus
import app.venues.event.service.EventPricingService
import app.venues.event.service.EventService
import app.venues.event.service.EventStatusService
import app.venues.seating.api.SeatingApi
import app.venues.shared.persistence.util.PageableMapper
import app.venues.venue.api.VenueApi
import app.venues.venue.api.service.VenueSecurityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
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
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/v1/staff/venues/{venueId}/events")
@Tag(
    name = "Venue Events",
    description = "Event management for venue owners"
)
class VenueEventController(
    private val eventService: EventService,
    private val eventPricingService: EventPricingService,
    private val eventStatusService: EventStatusService,
    private val venueSecurityService: VenueSecurityService,
    private val eventMapper: EventMapper,
    private val venueApi: VenueApi,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    @GetMapping
    @Operation(
        summary = "List venue events (staff)",
        description = "Returns all events for the venue, including drafts and suspended events."
    )
    fun listVenueEvents(
        @PathVariable venueId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) lang: String?,
        @RequestParam(required = false, name = "status") statuses: List<EventStatus>?
    ): ApiResponse<Page<EventSummaryResponse>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Listing staff events for venue=$venueId statuses=$statuses" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val events = eventService.getStaffEventSummariesByVenue(
            venueId = venueId,
            pageable = pageable,
            statuses = statuses?.toSet(),
            language = lang
        )

        return ApiResponse.success(
            data = events,
            message = "Events retrieved successfully"
        )
    }

    @GetMapping("/{eventId}")
    @Operation(
        summary = "Get event details (staff)",
        description = "Returns event details for staff, including hidden events."
    )
    fun getVenueEvent(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID,
        @RequestParam(required = false) lang: String?
    ): ApiResponse<EventResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Fetching staff event details: event=$eventId venue=$venueId" }

        val event = eventService.getEventForVenueStaff(eventId, venueId)
        val venueName = venueApi.getVenueName(venueId)
        val seatingChartName = event.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return ApiResponse.success(
            data = eventMapper.toResponse(event, venueName, seatingChartName, includeStats = true, language = lang),
            message = "Event retrieved successfully"
        )
    }

    /**
     * Create event for authenticated venue.
     */
    @PostMapping(consumes = ["multipart/form-data"])
    @Operation(
        summary = "Create event",
        description = "Create a new event for your venue (Venue owners only). Supports image upload."
    )
    fun createEvent(
        @PathVariable venueId: UUID,
        @Valid @RequestPart("data") request: EventRequest,
        @RequestPart("image", required = false) image: org.springframework.web.multipart.MultipartFile?,
        @RequestPart(
            "secondaryImages",
            required = false
        ) secondaryImages: List<org.springframework.web.multipart.MultipartFile>?,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<EventResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        logger.debug { "Creating event for venue: $venueId by staff: $staffId" }

        val event = eventService.createEvent(venueId, request, image, secondaryImages)

        val venueName = venueApi.getVenueName(venueId)
        val seatingChartName = event.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return ApiResponse.success(
            data = eventMapper.toResponse(event, venueName, seatingChartName),
            message = "Event created successfully"
        )
    }

    /**
     * Update event.
     */
    @PutMapping(value = ["/{eventId}"], consumes = ["multipart/form-data"])
    @Operation(
        summary = "Update event",
        description = "Update event details (Venue owners only). Supports image upload."
    )
    fun updateEvent(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @Valid @RequestPart("data") request: EventRequest,
        @RequestPart("image", required = false) image: org.springframework.web.multipart.MultipartFile?,
        @RequestPart(
            "secondaryImages",
            required = false
        ) secondaryImages: List<org.springframework.web.multipart.MultipartFile>?,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<EventResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Updating event: $eventId for venue: $venueId by staff: $staffId" }

        val event = eventService.updateEvent(eventId, venueId, request, image, secondaryImages)

        val venueName = venueApi.getVenueName(venueId)
        val seatingChartName = event.seatingChartId?.let { seatingApi.getSeatingChartName(it) }

        return ApiResponse.success(
            data = eventMapper.toResponse(event, venueName, seatingChartName),
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
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Deleting event: $eventId for venue: $venueId by staff: $staffId" }

        eventService.deleteEvent(eventId, venueId)

        return ApiResponse.success(
            data = Unit,
            message = "Event deleted successfully"
        )
    }

    // ===========================================
    // PRICE TEMPLATE MANAGEMENT
    // ===========================================

    @GetMapping("/{eventId}/price-templates")
    @Operation(summary = "List price templates")
    fun listPriceTemplates(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<List<PriceTemplateResponse>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        val templates = eventService.getPriceTemplates(eventId)
        val response = templates.map { eventMapper.toPriceTemplateResponse(it) }
        return ApiResponse.success(response, "Price templates retrieved")
    }


    @PostMapping("/{eventId}/price-templates")
    @Operation(summary = "Create price template")
    fun createPriceTemplate(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: PriceTemplateRequest,
        @RequestAttribute staffId: UUID
    ): ApiResponse<PriceTemplateResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        val template = eventService.createPriceTemplate(eventId, venueId, request)
        return ApiResponse.success(eventMapper.toPriceTemplateResponse(template), "Price template created")
    }

    @PutMapping("/{eventId}/price-templates/{templateId}")
    @Operation(summary = "Update price template")
    fun updatePriceTemplate(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @PathVariable templateId: UUID,
        @Valid @RequestBody request: PriceTemplateRequest,
        @RequestAttribute staffId: UUID
    ): ApiResponse<PriceTemplateResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        val template = eventService.updatePriceTemplate(eventId, venueId, templateId, request)
        return ApiResponse.success(eventMapper.toPriceTemplateResponse(template), "Price template updated")
    }

    @DeleteMapping("/{eventId}/price-templates/{templateId}")
    @Operation(summary = "Delete price template")
    fun deletePriceTemplate(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @PathVariable templateId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)
        eventService.deletePriceTemplate(eventId, venueId, templateId)
        return ApiResponse.success(Unit, "Price template deleted")
    }

    // ===========================================
    // PRICING MANAGEMENT
    // ===========================================

    /**
     * Assign price template to seats/tables.
     */
    @PutMapping("/{eventId}/sessions/{sessionId}/pricing")
    @Operation(
        summary = "Assign price template",
        description = "Batch assign a price template to seats, tables, or GA areas (Venue owners only)"
    )
    fun assignPriceTemplate(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: AssignPriceTemplateRequest,
        @RequestAttribute staffId: UUID,
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Assigning price template for session: $sessionId, event: $eventId by staff: $staffId" }

        eventService.assignPriceTemplate(
            eventId = eventId,
            sessionId = sessionId,
            venueId = venueId,
            templateId = request.templateId,
            seatIds = request.seatIds ?: emptyList(),
            tableIds = request.tableIds ?: emptyList(),
            gaIds = request.gaIds ?: emptyList()
        )

        return ApiResponse.success(
            data = Unit,
            message = "Price template assigned successfully"
        )
    }

    // ===========================================
    // EVENT-LEVEL PRICING CONFIGURATION
    // ===========================================

    /**
     * Get event-level pricing configuration.
     *
     * Aggregates pricing from all sessions:
     * - If all sessions have same template -> return that priceTemplateId
     * - If sessions differ -> priceTemplateId is null, isMixed is true
     */
    @GetMapping("/{eventId}/inventory")
    @Operation(
        summary = "Get pricing configuration",
        description = """
            Get aggregated pricing configuration across all sessions.
            
            Logic:
            - If all sessions have the same template for a seat -> return that priceTemplateId
            - If sessions differ -> return priceTemplateId: null, isMixed: true
            
            Does NOT include availability info (sold/reserved status).
            Use this when editing the Event to see the base configuration.
            "Mixed" tells the admin that sessions have diverged.
        """
    )
    fun getPricingConfiguration(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<EventPricingConfigurationResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Fetching pricing configuration for event: $eventId" }

        val configuration = eventPricingService.getPricingConfiguration(eventId, venueId)

        return ApiResponse.success(
            data = configuration,
            message = "Pricing configuration retrieved successfully"
        )
    }

    /**
     * Assign price template to ALL sessions of the event.
     */
    @PutMapping("/{eventId}/pricing")
    @Operation(
        summary = "Assign event-level pricing",
        description = """
            Batch assign a price template to seats, tables, or GA areas across ALL sessions.
            
            This applies the same pricing to every session of the event.
            Use this when setting up consistent pricing across all sessions.
        """
    )
    fun assignEventPricing(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: EventPricingAssignRequest,
        @RequestAttribute staffId: UUID
    ): ApiResponse<Unit> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Assigning event-level pricing for event: $eventId by staff: $staffId" }

        eventPricingService.assignEventPricing(eventId, venueId, request)

        return ApiResponse.success(
            data = Unit,
            message = "Event pricing assigned successfully"
        )
    }

    // ===========================================
    // STATUS MANAGEMENT
    // ===========================================

    /**
     * Change event status.
     * Allows staff to transition events between statuses with validation.
     */
    @PutMapping("/{eventId}/status")
    @Operation(
        summary = "Change event status",
        description = """
            Update event status with proper validation.
            
            Allowed transitions:
            - DRAFT → PUBLISHED (publish event)
            - DRAFT → DELETED (discard draft)
            - PUBLISHED → SUSPENDED (temporary removal)
            - PUBLISHED → ARCHIVED (event finished)
            - PUBLISHED → DELETED (permanent removal)
            - SUSPENDED → PUBLISHED (resume event)
            - SUSPENDED → DELETED (permanent removal)
            - ARCHIVED → DELETED (cleanup)
            
            Validation errors will be returned if transition is not allowed.
        """
    )
    fun changeEventStatus(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: EventStatusChangeRequest,
        @RequestAttribute staffId: UUID
    ): ApiResponse<StatusChangeResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Changing event status: eventId=$eventId, targetStatus=${request.status}, staff=$staffId" }

        val event = eventStatusService.changeEventStatus(
            eventId = eventId,
            venueId = venueId,
            targetStatus = request.status,
            reason = request.reason
        )

        val allowedTransitions = eventStatusService.getAllowedEventTransitions(eventId, venueId)

        return ApiResponse.success(
            data = StatusChangeResponse(
                success = true,
                currentStatus = event.status.name,
                allowedTransitions = allowedTransitions.map { it.name },
                message = "Event status changed to ${event.status}"
            ),
            message = "Event status updated successfully"
        )
    }

    /**
     * Get allowed event status transitions.
     * Returns possible status changes from current state.
     */
    @GetMapping("/{eventId}/status/transitions")
    @Operation(
        summary = "Get allowed event status transitions",
        description = "Returns list of statuses that the event can transition to from its current state."
    )
    fun getAllowedEventTransitions(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<List<EventStatus>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val allowedTransitions = eventStatusService.getAllowedEventTransitions(eventId, venueId)

        return ApiResponse.success(
            data = allowedTransitions.toList(),
            message = "Allowed transitions retrieved"
        )
    }

    /**
     * Change session status.
     * Allows staff to transition sessions between statuses with validation.
     */
    @PutMapping("/{eventId}/sessions/{sessionId}/status")
    @Operation(
        summary = "Change session status",
        description = """
            Update session status with proper validation.
            
            Allowed transitions:
            - ON_SALE → PAUSED (temporary halt)
            - ON_SALE → SOLD_OUT (mark sold out)
            - ON_SALE → SALES_CLOSED (session started)
            - ON_SALE → CANCELLED (cancel session)
            - PAUSED → ON_SALE (resume sales)
            - PAUSED → SOLD_OUT, SALES_CLOSED, CANCELLED
            - SOLD_OUT → SALES_CLOSED, CANCELLED
            - SALES_CLOSED → CANCELLED
            
            Cancelling a session will trigger refund workflows.
        """
    )
    fun changeSessionStatus(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SessionStatusChangeRequest,
        @RequestAttribute staffId: UUID
    ): ApiResponse<StatusChangeResponse> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        logger.debug { "Changing session status: sessionId=$sessionId, targetStatus=${request.status}, staff=$staffId" }

        val session = eventStatusService.changeSessionStatus(
            sessionId = sessionId,
            venueId = venueId,
            targetStatus = request.status,
            reason = request.reason
        )

        val allowedTransitions = eventStatusService.getAllowedSessionTransitions(sessionId, venueId)

        return ApiResponse.success(
            data = StatusChangeResponse(
                success = true,
                currentStatus = session.status.name,
                allowedTransitions = allowedTransitions.map { it.name },
                message = "Session status changed to ${session.status}"
            ),
            message = "Session status updated successfully"
        )
    }

    /**
     * Get allowed session status transitions.
     * Returns possible status changes from current state.
     */
    @GetMapping("/{eventId}/sessions/{sessionId}/status/transitions")
    @Operation(
        summary = "Get allowed session status transitions",
        description = "Returns list of statuses that the session can transition to from its current state."
    )
    fun getAllowedSessionTransitions(
        @PathVariable venueId: UUID,
        @PathVariable eventId: UUID,
        @PathVariable sessionId: UUID,
        @RequestAttribute staffId: UUID
    ): ApiResponse<List<SessionStatus>> {
        venueSecurityService.requireVenueManagementPermission(staffId, venueId)

        val allowedTransitions = eventStatusService.getAllowedSessionTransitions(sessionId, venueId)

        return ApiResponse.success(
            data = allowedTransitions.toList(),
            message = "Allowed transitions retrieved"
        )
    }
}