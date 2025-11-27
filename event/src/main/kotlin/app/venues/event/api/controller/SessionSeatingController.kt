package app.venues.event.api.controller

import app.venues.common.model.ApiResponse
import app.venues.event.api.dto.SeatAvailabilityResponse
import app.venues.event.api.dto.SessionInventoryResponse
import app.venues.event.service.SessionSeatingService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

/**
 * Controller for session inventory operations (Split Strategy - Dynamic Layer).
 *
 * Provides real-time inventory state designed to work with cached static structure:
 * - Static: GET /seating-charts/{chartId}/structure (cached 24h, seating module)
 * - Dynamic: GET /sessions/{sessionId}/inventory (real-time, this controller)
 *
 * Optimized for high-traffic large venues (10k+ seats).
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Session Inventory", description = "Real-time inventory state for event sessions")
class SessionSeatingController(
    private val sessionSeatingService: SessionSeatingService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Get lightweight session inventory (Split Strategy - Dynamic Layer).
     *
     * Returns only dynamic state (status, pricing) without geometry data.
     * Designed to work alongside cached static structure:
     * - Static: GET /seating-charts/{chartId}/structure (cached 24h)
     * - Dynamic: GET /sessions/{sessionId}/inventory (real-time)
     *
     * Client merges the two responses using ID matching for optimal performance.
     *
     * @param sessionId Event session ID
     * @return Lightweight inventory state keyed by ID
     */
    @GetMapping("/{sessionId}/inventory")
    @Operation(
        summary = "Get session inventory (real-time)",
        description = """
            Get lightweight session inventory for real-time status updates.
            
            This endpoint is part of the Split Strategy for high-performance venues:
            1. Cache static structure from GET /seating-charts/{chartId}/structure
            2. Poll this endpoint for real-time status/pricing
            3. Merge client-side using ID matching
            
            Returns:
            - Seat states (status, price, color, templateName) keyed by seat ID
            - Table states keyed by table ID
            - GA area states keyed by GA area ID
            - Price templates with override information
            - Quick statistics
            
            NO geometry data (X, Y, rotation) is included.
            Prices are in cents (smallest currency unit) for efficient transfer.
            Status codes: A=Available, R=Reserved, S=Sold, B=Blocked, C=Closed
        """
    )
    fun getSessionInventory(
        @PathVariable sessionId: UUID
    ): ApiResponse<SessionInventoryResponse> {
        logger.debug { "Fetching session inventory for session: $sessionId" }

        val inventory = sessionSeatingService.getSessionInventory(sessionId)

        return ApiResponse.success(
            data = inventory,
            message = "Session inventory retrieved successfully"
        )
    }

    /**
     * Get quick availability statistics.
     *
     * Returns availability counts without full inventory detail.
     * Optimized for quick checks (event listings, availability badges).
     *
     * @param sessionId Event session ID
     * @return Availability statistics
     */
    @GetMapping("/{sessionId}/availability")
    @Operation(
        summary = "Get seat availability (quick stats)",
        description = """
            Get quick availability statistics for a session.
            
            Returns:
            - Total seat count
            - Available seat count
            - Sold seat count
            - Reserved seat count
            
            Use for:
            - Event listing badges ("X seats left")
            - Quick availability checks
            - Dashboard statistics
        """
    )
    fun getSessionAvailability(
        @PathVariable sessionId: UUID
    ): ApiResponse<SeatAvailabilityResponse> {
        logger.debug { "Fetching availability for session: $sessionId" }

        val availability = sessionSeatingService.getSessionAvailability(sessionId)

        return ApiResponse.success(
            data = availability,
            message = "Availability retrieved successfully"
        )
    }
}

