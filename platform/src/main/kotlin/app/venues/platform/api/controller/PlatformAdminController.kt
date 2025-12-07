package app.venues.platform.api.controller

import app.venues.common.model.ApiResponse
import app.venues.platform.api.dto.*
import app.venues.platform.security.NonceService
import app.venues.platform.security.NonceStats
import app.venues.platform.service.PlatformService
import app.venues.platform.webhook.WebhookService
import app.venues.shared.persistence.util.PageableMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Admin controller for platform management.
 *
 * Only accessible by ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/platforms")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platform Management (Admin)", description = "Manage external platform integrations")
@SecurityRequirement(name = "bearerAuth")
class PlatformAdminController(
    private val platformService: PlatformService,
    private val nonceService: NonceService,
    private val webhookService: WebhookService,
    private val platformRepository: app.venues.platform.repository.PlatformRepository,
    private val webhookEventRepository: app.venues.platform.repository.WebhookEventRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Create a new platform
     */
    @PostMapping
    @Operation(
        summary = "Create platform",
        description = "Register a new external platform. Returns the shared secret (only shown once)."
    )
    fun createPlatform(
        @Valid @RequestBody request: CreatePlatformRequest
    ): ApiResponse<PlatformWithSecretResponse> {
        logger.debug { "Creating platform: ${request.name}" }

        val result = platformService.createPlatform(request)

        return ApiResponse.success(
            data = result,
            message = "Platform created successfully. Save the shared secret - it will not be shown again!"
        )
    }

    /**
     * Get all platforms
     */
    @GetMapping
    @Operation(
        summary = "List platforms",
        description = "Get all registered platforms"
    )
    fun getAllPlatforms(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<PlatformResponse>> {
        logger.debug { "Fetching all platforms" }

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val platforms = platformService.getAllPlatforms(pageable)

        return ApiResponse.success(
            data = platforms,
            message = "Platforms retrieved successfully"
        )
    }

    /**
     * Get platform by ID
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get platform",
        description = "Get platform details by ID"
    )
    fun getPlatformById(@PathVariable id: UUID): ApiResponse<PlatformResponse> {
        logger.debug { "Fetching platform: $id" }

        val platform = platformService.getPlatformById(id)

        return ApiResponse.success(
            data = platform,
            message = "Platform retrieved successfully"
        )
    }

    /**
     * Update platform
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Update platform",
        description = "Update platform configuration"
    )
    fun updatePlatform(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdatePlatformRequest
    ): ApiResponse<PlatformResponse> {
        logger.debug { "Updating platform: $id" }

        val result = platformService.updatePlatform(id, request)

        return ApiResponse.success(
            data = result,
            message = "Platform updated successfully"
        )
    }

    /**
     * Regenerate shared secret
     */
    @PostMapping("/{id}/regenerate-secret")
    @Operation(
        summary = "Regenerate shared secret",
        description = "Generate a new shared secret for the platform. Old secret will be invalidated immediately!"
    )
    fun regenerateSharedSecret(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RegenerateSecretRequest
    ): ApiResponse<PlatformWithSecretResponse> {
        logger.debug { "Regenerating secret for platform: $id" }

        val result = platformService.regenerateSharedSecret(id, request)

        return ApiResponse.success(
            data = result,
            message = "Shared secret regenerated successfully. Update the platform configuration immediately!"
        )
    }

    /**
     * Delete platform
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete platform",
        description = "Delete a platform (cannot be undone)"
    )
    fun deletePlatform(@PathVariable id: UUID): ApiResponse<Unit> {
        logger.debug { "Deleting platform: $id" }

        platformService.deletePlatform(id)

        return ApiResponse.success(
            data = Unit,
            message = "Platform deleted successfully"
        )
    }

    /**
     * Get nonce cache statistics (for monitoring)
     */
    @GetMapping("/nonce-stats")
    @Operation(
        summary = "Get nonce statistics",
        description = "Get statistics about active nonces in the cache (for monitoring)"
    )
    fun getNonceStatistics(): ApiResponse<NonceStats> {
        logger.debug { "Fetching nonce statistics" }

        val stats = nonceService.getNonceStats()

        return ApiResponse.success(
            data = stats,
            message = "Nonce statistics retrieved successfully"
        )
    }

    /**
     * Clear nonces for specific platform (emergency use only)
     */
    @DeleteMapping("/{id}/nonces")
    @Operation(
        summary = "Clear nonces for platform",
        description = "Clear all active nonces for a specific platform. Use with caution - only for emergency situations!"
    )
    fun clearPlatformNonces(@PathVariable id: Long): ApiResponse<Map<String, Any>> {
        logger.warn { "Clearing nonces for platform: $id" }

        val count = nonceService.clearNoncesForPlatform(id)

        return ApiResponse.success(
            data = mapOf(
                "platformId" to id,
                "clearedNonces" to count
            ),
            message = "Cleared $count nonces for platform $id"
        )
    }

    /**
     * List webhook events for a platform.
     */
    @GetMapping("/{id}/webhooks")
    @Operation(
        summary = "List platform webhooks",
        description = "List webhook delivery attempts for a platform with optional status filter"
    )
    fun listWebhooks(
        @PathVariable id: UUID,
        @RequestParam(required = false) status: app.venues.platform.domain.WebhookStatus?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<WebhookEventResponse>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val page = if (status != null) {
            webhookEventRepository.findByPlatformIdAndStatus(id, status, pageable)
        } else {
            webhookEventRepository.findByPlatformId(id, pageable)
        }

        val platformName = platformRepository.findById(id).map { it.name }.orElse("")

        val mapped = page.map { event ->
            WebhookEventResponse(
                id = event.id!!,
                platformId = event.platformId,
                platformName = platformName,
                eventType = event.eventType,
                sessionId = event.sessionId,
                seatIdentifier = event.seatCode,
                levelIdentifier = event.gaAreaCode,
                tableIdentifier = event.tableCode,
                status = event.status,
                responseCode = event.responseCode,
                errorMessage = event.errorMessage,
                attemptCount = event.attemptCount,
                nextRetryAt = event.nextRetryAt?.toString(),
                createdAt = event.createdAt.toString()
            )
        }

        return ApiResponse.success(
            data = mapped,
            message = "Webhook events retrieved"
        )
    }

    /**
     * Replay a specific webhook event.
     */
    @PostMapping("/{id}/webhooks/{eventId}/replay")
    @Operation(
        summary = "Replay webhook",
        description = "Re-deliver a webhook event for troubleshooting"
    )
    fun replayWebhook(
        @PathVariable id: UUID,
        @PathVariable eventId: UUID
    ): ApiResponse<Unit> {
        webhookService.replayWebhook(eventId)
        return ApiResponse.success(data = Unit, message = "Webhook replay triggered")
    }
}
