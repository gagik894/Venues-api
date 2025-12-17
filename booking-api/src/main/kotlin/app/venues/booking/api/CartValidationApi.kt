package app.venues.booking.api

import java.util.*

/**
 * Public API for validating platform carts without exposing booking internals.
 */
interface CartValidationApi {
    /**
     * Validates that a cart exists, belongs to the platform, is not expired,
     * is not empty, and optionally matches an expected session.
     *
     * @throws app.venues.common.exception.VenuesException on any validation failure.
     */
    fun validateCartForPlatform(token: UUID, platformId: UUID, expectedSessionId: UUID? = null): CartValidationResult
}

data class CartValidationResult(
    val token: UUID,
    val sessionId: UUID,
    val expiresAt: String
)

