package app.venues.platform.api.controller

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.DirectSaleRequest
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
 * Platform "Advanced Mode" Controller (Cart Flow).
 *
 * Full Cart lifecycle:
 * 1. /cart/hold -> Create/Update Cart
 * 2. /cart/checkout -> Validate & Prepare
 * 3. /cart/confirm -> Convert Cart to Booking
 * 4. /cart/release -> Delete Cart
 */
@RestController
@RequestMapping("/api/v1/platforms/advanced")
@Tag(name = "Platform API (Advanced Mode)", description = "Full Cart-based booking flow")
class PlatformAdvancedController(
    private val platformBookingService: PlatformBookingService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * /hold-simple - Create cart and reserve inventory with optional TTL override.
     */
    @PostMapping("/cart/hold-simple")
    @Operation(
        summary = "Hold inventory (Batch)",
        description = "Create cart and reserve seats/GA/tables with optional TTL override. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    @Auditable(action = "PLATFORM_ADV_HOLD_SIMPLE", subjectType = "event_session", includeVenueId = false)
    fun holdSimple(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuditMetadata("request") @Valid @RequestBody request: PlatformHoldRequest
    ): ApiResponse<PlatformHoldResponse> {
        logger.debug { "Platform $platformId hold-simple for session ${request.sessionId}" }

        val result = platformBookingService.holdSimple(platformId, request, idempotencyKey)

        return ApiResponse.success(
            data = result,
            message = "Inventory reserved successfully"
        )
    }

    /**
     * /hold - Create cart and reserve inventory.
     */
    @PostMapping("/cart/hold")
    @Operation(
        summary = "Hold inventory (Granular)",
        description = "Create cart and reserve seats, GA tickets, or tables. Cart expires in 7 minutes. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    @Auditable(action = "PLATFORM_ADV_HOLD", subjectType = "event_session", includeVenueId = false)
    fun hold(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuditMetadata("request") @Valid @RequestBody request: PlatformHoldRequest
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
     */
    @PostMapping("/cart/checkout")
    @Operation(
        summary = "Checkout cart",
        description = "Validate cart and prepare for payment. Returns final pricing and guest details. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    @Auditable(action = "PLATFORM_ADV_CHECKOUT", subjectType = "booking", includeVenueId = false)
    fun checkout(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuditMetadata("request") @Valid @RequestBody request: PlatformCheckoutRequest
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
     */
    @PostMapping("/cart/confirm")
    @Operation(
        summary = "Confirm cart to booking",
        description = "Confirm booking with payment proof. Creates booking, finalizes inventory, generates tickets. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    @Auditable(action = "PLATFORM_ADV_CONFIRM", subjectType = "booking", includeVenueId = false)
    fun confirm(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuditMetadata("request") @Valid @RequestBody request: PlatformConfirmRequest
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
     */
    @PostMapping("/cart/release")
    @Operation(
        summary = "Release cart",
        description = "Release held inventory and delete cart. Use when customer cancels or payment fails. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    @Auditable(action = "PLATFORM_ADV_RELEASE", subjectType = "event_session", includeVenueId = false)
    fun release(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuditMetadata("request") @Valid @RequestBody request: PlatformReleaseRequest
    ): ApiResponse<PlatformReleaseResponse> {
        logger.debug { "Platform $platformId releasing cart ${request.reservationToken}" }

        val result = platformBookingService.release(platformId, request, idempotencyKey)

        return ApiResponse.success(
            data = result,
            message = "Inventory released successfully"
        )
    }

    /**
     * /direct - Create confirmed booking directly (skip cart).
     * Kept for backward compatibility or advanced "one-shot" use cases.
     */
    @PostMapping("/direct")
    @Operation(
        summary = "Direct confirmed booking",
        description = "Create a confirmed booking directly without cart. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    @Auditable(action = "PLATFORM_ADV_DIRECT_BOOKING", subjectType = "booking", includeVenueId = false)
    fun directBooking(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuditMetadata("request") @Valid @RequestBody request: DirectSaleRequest
    ): ApiResponse<BookingResponse> {
        logger.debug { "Platform $platformId direct booking for session ${request.sessionId}" }

        val result = platformBookingService.directBooking(platformId, request, idempotencyKey)

        return ApiResponse.success(
            data = result,
            message = "Booking created successfully"
        )
    }
}
