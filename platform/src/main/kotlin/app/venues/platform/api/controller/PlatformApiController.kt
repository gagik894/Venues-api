package app.venues.platform.api.controller

import app.venues.common.model.ApiResponse
import app.venues.platform.api.dto.*
import app.venues.platform.service.PlatformService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Platform API controller for external platform integrations.
 *
 * Endpoints used by external platforms to make reservations.
 * Authentication is done via platform-specific API keys.
 */
@RestController
@RequestMapping("/api/v1/platforms")
@Tag(name = "Platform API", description = "API endpoints for external platform integrations")
class PlatformApiController(
    private val platformService: PlatformService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Reserve seats/tickets via platform API
     *
     * External platforms use this endpoint to reserve seats on behalf of their users.
     * Authentication is done via X-Platform-ID header and HMAC signature validation.
     */
    @PostMapping("/reserve")
    @Operation(
        summary = "Reserve seats (Platform API)",
        description = "Reserve seats or GA tickets for an event session. Requires platform authentication."
    )
    @SecurityRequirement(name = "platformAuth")
    fun reserveSeats(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @Valid @RequestBody request: PlatformReservationRequest
    ): ApiResponse<PlatformReservationResponse> {
        logger.debug { "Platform $platformId requesting reservation for session ${request.sessionId}" }

        val result = platformService.reserveSeats(platformId, request)

        return ApiResponse.success(
            data = result,
            message = result.message
        )
    }

    /**
     * Release seats/tickets via platform API
     *
     * External platforms use this endpoint to release previously reserved seats.
     * Authentication is done via X-Platform-ID header and HMAC signature validation.
     */
    @PostMapping("/release")
    @Operation(
        summary = "Release reservation (Platform API)",
        description = "Release a held reservation. Requires platform authentication with HMAC signature."
    )
    @SecurityRequirement(name = "platformAuth")
    fun releaseSeats(
        @RequestHeader("X-Platform-ID") platformId: UUID,
        @Valid @RequestBody request: PlatformReleaseRequest
    ): ApiResponse<PlatformReleaseResponse> {
        logger.debug { "Platform $platformId releasing reservation ${request.reservationToken}" }

        val result = platformService.releaseSeats(platformId, request)

        return ApiResponse.success(
            data = result,
            message = result.message
        )
    }

    /**
     * Sell seats/tickets via platform API
     *
     * External platforms use this endpoint to convert a reservation into a confirmed sale.
     * Authentication is done via X-Platform-ID header and HMAC signature validation.
     */
    @PostMapping("/sell")
    @Operation(
        summary = "Sell seats (Platform API)",
        description = "Convert reservation to confirmed booking. Requires platform authentication with HMAC signature."
    )
    @SecurityRequirement(name = "platformAuth")
    fun sellSeats(
        @RequestHeader("X-Platform-ID") platformId: Long,
        @Valid @RequestBody request: PlatformSellRequest
    ): ApiResponse<PlatformSellResponse> {
        logger.debug { "Platform $platformId selling reservation ${request.reservationToken}" }

        val result = platformService.sellSeats(platformId, request)

        return ApiResponse.success(
            data = result,
            message = result.message
        )
    }
}
