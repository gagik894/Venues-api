package app.venues.shared.web.controller

import app.venues.common.constants.AppConstants
import app.venues.common.model.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Health check controller for system status monitoring.
 *
 * Provides simple endpoints to verify that the API is running and responding.
 * These endpoints are typically used by:
 * - Load balancers for health checks
 * - Monitoring systems for uptime tracking
 * - DevOps teams for deployment verification
 *
 * All endpoints in this controller are public (no authentication required).
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "System health and status endpoints")
class HealthController {

    private val logger = KotlinLogging.logger {}
    private val startTime: Instant = Instant.now()

    /**
     * Simple health check endpoint.
     *
     * Returns a basic response indicating the service is alive and responding.
     * This is the minimal health check for load balancer status monitoring.
     *
     * @return ResponseEntity with health status
     */
    @GetMapping("/ping")
    @Operation(
        summary = "Ping health check",
        description = "Returns a simple pong response to verify the service is running"
    )
    fun ping(): ResponseEntity<ApiResponse<Unit>> {
        logger.debug { "Health check ping received" }

        return ResponseEntity.ok(
            ApiResponse.success("pong")
        )
    }

    /**
     * Detailed status endpoint.
     *
     * Returns comprehensive status information including:
     * - Service status
     * - Application version
     * - Uptime
     * - Server timestamp
     *
     * @return ResponseEntity with detailed status information
     */
    @GetMapping("/status")
    @Operation(
        summary = "Detailed status check",
        description = "Returns detailed information about the service status and uptime"
    )
    fun status(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        logger.debug { "Status check requested" }

        val now = Instant.now()
        val uptimeSeconds = java.time.Duration.between(startTime, now).seconds

        val statusInfo = mapOf(
            "status" to AppConstants.Status.UP,
            "service" to AppConstants.Application.NAME,
            "description" to AppConstants.Application.DESCRIPTION,
            "version" to AppConstants.Application.VERSION,
            "uptime" to formatUptime(uptimeSeconds),
            "uptimeSeconds" to uptimeSeconds
        )

        return ResponseEntity.ok(
            ApiResponse.success(statusInfo)
        )
    }

    /**
     * Formats uptime in human-readable format.
     *
     * @param seconds Total uptime in seconds
     * @return Formatted string (e.g., "2d 5h 30m 15s")
     */
    private fun formatUptime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            append("${secs}s")
        }.trim()
    }
}

