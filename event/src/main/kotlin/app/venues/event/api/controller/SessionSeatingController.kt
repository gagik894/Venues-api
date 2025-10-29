package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.event.api.dto.SeatAvailabilityResponse
import app.venues.event.api.dto.SessionSeatingResponse
import app.venues.event.service.SessionSeatingService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for session seating operations.
 *
 * Provides endpoints for retrieving seating charts with session-specific data:
 * - Pricing per seat
 * - Seat availability status
 * - Level hierarchy with positions
 * - Price templates
 *
 * Optimized for large venues (10k+ seats).
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Session Seating", description = "Seating charts for event sessions with pricing and availability")
class SessionSeatingController(
    private val sessionSeatingService: SessionSeatingService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Get complete seating chart for a session.
     *
     * Returns the full seating structure with:
     * - Level hierarchy with positions
     * - Seats with positions, pricing, and status
     * - Price templates
     * - Availability statistics
     *
     * Use this endpoint to display the interactive seating map to users.
     *
     * @param sessionId Event session ID
     * @return Complete seating structure with session-specific data
     */
    @GetMapping("/{sessionId}/seating")
    @Operation(
        summary = "Get session seating chart",
        description = """
            Get complete seating chart for an event session including:
            - Level hierarchy (sections, rows, etc.)
            - Seat positions and identifiers
            - Seat status (available, reserved, sold)
            - Pricing per seat
            - Price templates
            - GA (General Admission) capacity
            
            Optimized for large venues with 10k+ seats.
        """
    )
    fun getSessionSeating(
        @PathVariable sessionId: Long
    ): ApiResponse<SessionSeatingResponse> {
        logger.debug { "Fetching session seating for session: $sessionId" }

        val seating = sessionSeatingService.getSessionSeating(sessionId)

        return ApiResponse.success(
            data = seating,
            message = "Session seating retrieved successfully"
        )
    }

    /**
     * Get quick availability info for a session.
     *
     * Returns availability statistics without the full seating structure.
     * Much faster than getSessionSeating() for large venues.
     *
     * Use this endpoint for:
     * - Checking if tickets are available
     * - Displaying availability count in listings
     * - Quick availability checks before showing full seating map
     *
     * @param sessionId Event session ID
     * @return Availability statistics
     */
    @GetMapping("/{sessionId}/availability")
    @Operation(
        summary = "Get seat availability",
        description = """
            Get quick availability statistics for a session without loading full seating structure.
            
            Returns:
            - Total seat count
            - Available seat count
            - Sold seat count
            - Reserved seat count
            - Available seat identifiers (for small venues only)
            
            Much faster than loading full seating chart.
        """
    )
    fun getSessionAvailability(
        @PathVariable sessionId: Long
    ): ApiResponse<SeatAvailabilityResponse> {
        logger.debug { "Fetching availability for session: $sessionId" }

        val availability = sessionSeatingService.getSessionAvailability(sessionId)

        return ApiResponse.success(
            data = availability,
            message = "Availability retrieved successfully"
        )
    }
}

