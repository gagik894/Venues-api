package app.venues.platform.api.controller

import app.venues.booking.api.dto.BookingResponse
import app.venues.common.model.ApiResponse
import app.venues.platform.api.dto.PlatformEasyReserveRequest
import app.venues.platform.service.PlatformBookingService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Platform "Easy Mode" Controller.
 *
 * Simple 3-step flow:
 * 1. /reserve -> Create PENDING booking (items reserved)
 * 2. /confirm -> Mark booking as SOLD (generate tickets)
 * 3. /release -> Cancel booking (release items)
 */
@RestController
@RequestMapping("/api/v1/platforms/easy")
@Tag(name = "Platform API (Easy Mode)", description = "Simplified reserve/confirm flow")
class PlatformEasyController(
    private val platformBookingService: PlatformBookingService
) {
    private val logger = KotlinLogging.logger {}

    @PostMapping("/reserve")
    @Operation(summary = "Reserve items (Create Pending Booking)")
    @SecurityRequirement(name = "platformAuth")
    fun reserve(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PlatformEasyReserveRequest
    ): ApiResponse<BookingResponse> {
        logger.debug { "Platform $platformId easy-reserve for session ${request.sessionId}" }
        val result = platformBookingService.reserveSimple(platformId, request, idempotencyKey)
        return ApiResponse.success(result, "Booking reserved successfully")
    }

    @PostMapping("/confirm/{bookingId}")
    @Operation(summary = "Confirm booking (Mark as Sold)")
    @SecurityRequirement(name = "platformAuth")
    fun confirm(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @PathVariable bookingId: UUID
    ): ApiResponse<BookingResponse> {
        logger.debug { "Platform $platformId easy-confirm booking $bookingId" }
        val result = platformBookingService.confirmSimple(platformId, bookingId, idempotencyKey)
        return ApiResponse.success(result, "Booking confirmed successfully")
    }

    @PostMapping("/release/{bookingId}")
    @Operation(summary = "Release booking (Cancel)")
    @SecurityRequirement(name = "platformAuth")
    fun release(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @PathVariable bookingId: UUID
    ): ApiResponse<BookingResponse> {
        logger.debug { "Platform $platformId easy-release booking $bookingId" }
        val result = platformBookingService.releaseSimple(platformId, bookingId, idempotencyKey)
        return ApiResponse.success(result, "Booking released successfully")
    }
}
