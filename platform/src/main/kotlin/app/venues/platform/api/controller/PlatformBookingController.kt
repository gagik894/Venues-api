package app.venues.platform.api.controller

import app.venues.common.model.ApiResponse
import app.venues.platform.api.dto.*
import app.venues.platform.service.PlatformBookingService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Platform Booking API Controller (/hold, /checkout, /confirm flow).
 *
 * Government-quality secure booking flow for external platform integrations.
 * Replaces legacy /reserve, /release, /sell endpoints with improved security.
 *
 * Flow:
 * 1. /hold - Create cart, reserve inventory, bind to platform
 * 2. /checkout - Validate cart, calculate final pricing, prepare for payment
 * 3. /confirm - Create booking, finalize inventory, generate tickets
 *
 * Security:
 * - Cart-platform binding prevents cross-platform theft
 * - Atomic idempotency prevents duplicate bookings
 * - Request body hash in HMAC prevents tampering
 * - Pessimistic locking prevents concurrent confirmation races
 */
@RestController
@RequestMapping("/api/v1/platforms/bookings")
@Tag(name = "Platform Booking API", description = "Secure booking flow for external platforms")
class PlatformBookingController(
    private val platformBookingService: PlatformBookingService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * /hold - Create cart and reserve inventory.
     *
     * Creates a new cart (or adds to existing) and reserves seats/GA/tables.
     * Cart is bound to the requesting platform and cannot be accessed by others.
     *
     * Idempotent: Use same Idempotency-Key to safely retry on network failures.
     */
    @PostMapping("/hold")
    @Operation(
        summary = "Hold inventory (Platform API)",
        description = "Create cart and reserve seats, GA tickets, or tables. Cart expires in 20 minutes. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    fun hold(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PlatformHoldRequest
    ): ApiResponse<PlatformHoldResponse> {
        logger.debug { "Platform $platformId holding inventory for session ${request.sessionId}" }

        val result = platformBookingService.hold(platformId, request, idempotencyKey)

        return ApiResponse.success(
            data = result,
            message = "Inventory reserved successfully"
        )
    }

    /**
     * /checkout - Validate cart and prepare for payment.
     *
     * Validates cart contents, calculates final pricing with any discounts,
     * and prepares booking for payment confirmation.
     *
     * Idempotent: Use same Idempotency-Key to safely retry.
     */
    @PostMapping("/checkout")
    @Operation(
        summary = "Checkout cart (Platform API)",
        description = "Validate cart and prepare for payment. Returns final pricing and guest details. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    fun checkout(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PlatformCheckoutRequest
    ): ApiResponse<PlatformCheckoutResponse> {
        logger.debug { "Platform $platformId checking out cart ${request.holdToken}" }

        val result = platformBookingService.checkout(platformId, request, idempotencyKey)

        return ApiResponse.success(
            data = result,
            message = "Checkout successful"
        )
    }

    /**
     * /confirm - Confirm booking with payment proof.
     *
     * Creates confirmed booking, finalizes inventory (RESERVED -> SOLD),
     * generates tickets, and sends confirmation email.
     *
     * Idempotent: Use same Idempotency-Key to prevent duplicate bookings.
     */
    @PostMapping("/confirm")
    @Operation(
        summary = "Confirm booking (Platform API)",
        description = "Confirm booking with payment proof. Creates booking, finalizes inventory, generates tickets. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    fun confirm(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PlatformConfirmRequest
    ): ApiResponse<PlatformConfirmResponse> {
        logger.debug { "Platform $platformId confirming booking for cart ${request.holdToken}" }

        val result = platformBookingService.confirm(platformId, request, idempotencyKey)

        return ApiResponse.success(
            data = result,
            message = "Booking confirmed successfully"
        )
    }

    /**
     * /release - Release held inventory.
     *
     * Releases cart and returns inventory to available pool.
     * Use this when customer cancels or payment fails.
     */
    @PostMapping("/release")
    @Operation(
        summary = "Release hold (Platform API)",
        description = "Release held inventory and delete cart. Use when customer cancels or payment fails. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    fun release(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PlatformReleaseRequest
    ): ApiResponse<PlatformReleaseResponse> {
        logger.debug { "Platform $platformId releasing cart ${request.reservationToken}" }

        val result = platformBookingService.release(platformId, request, idempotencyKey)

        return ApiResponse.success(
            data = result,
            message = "Inventory released successfully"
        )
    }
}
