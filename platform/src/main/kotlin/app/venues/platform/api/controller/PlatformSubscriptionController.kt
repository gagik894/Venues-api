package app.venues.platform.api.controller

import app.venues.common.model.ApiResponse
import app.venues.platform.api.dto.BulkSubscribeRequest
import app.venues.platform.api.dto.CreateSubscriptionRequest
import app.venues.platform.api.dto.PlatformSubscriptionResponse
import app.venues.platform.service.WebhookSubscriptionService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Admin controller for managing platform webhook subscriptions.
 */
@RestController
@RequestMapping("/api/v1/admin/platforms/{platformId}/subscriptions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platform Subscriptions (Admin)", description = "Manage platform event subscriptions")
@SecurityRequirement(name = "bearerAuth")
class PlatformSubscriptionController(
    private val subscriptionService: WebhookSubscriptionService
) {
    private val logger = KotlinLogging.logger {}

    @GetMapping
    @Operation(summary = "List subscriptions", description = "Get all event subscriptions for a platform")
    fun getSubscriptions(@PathVariable platformId: UUID): ApiResponse<List<PlatformSubscriptionResponse>> {
        val subscriptions = subscriptionService.getSubscriptions(platformId)
        return ApiResponse.success(subscriptions, "Subscriptions retrieved")
    }

    @PostMapping
    @Operation(summary = "Subscribe to event", description = "Subscribe a platform to a specific event")
    fun subscribe(
        @PathVariable platformId: UUID,
        @Valid @RequestBody request: CreateSubscriptionRequest
    ): ApiResponse<PlatformSubscriptionResponse> {
        val result = subscriptionService.subscribe(platformId, request.eventId)
        return ApiResponse.success(result, "Subscribed successfully")
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk subscribe", description = "Subscribe a platform to multiple events")
    fun bulkSubscribe(
        @PathVariable platformId: UUID,
        @Valid @RequestBody request: BulkSubscribeRequest
    ): ApiResponse<Map<String, Int>> {
        val count = subscriptionService.bulkSubscribe(platformId, request.eventIds)
        return ApiResponse.success(mapOf("subscribedCount" to count), "Bulk subscription successful")
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "Unsubscribe from event", description = "Unsubscribe a platform from an event")
    fun unsubscribe(
        @PathVariable platformId: UUID,
        @PathVariable eventId: UUID
    ): ApiResponse<Unit> {
        subscriptionService.unsubscribe(platformId, eventId)
        return ApiResponse.success(Unit, "Unsubscribed successfully")
    }
}

