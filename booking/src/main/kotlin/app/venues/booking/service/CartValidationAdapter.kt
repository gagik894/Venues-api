package app.venues.booking.service

import app.venues.booking.api.CartValidationApi
import app.venues.booking.api.CartValidationResult
import app.venues.booking.repository.CartRepository
import app.venues.common.exception.VenuesException
import org.springframework.stereotype.Service
import java.util.*

/**
 * Adapter exposing cart validation to external modules via CartValidationApi.
 */
@Service
class CartValidationAdapter(
    private val cartRepository: CartRepository
) : CartValidationApi {

    override fun validateCartForPlatform(
        token: UUID,
        platformId: UUID,
        expectedSessionId: UUID?
    ): CartValidationResult {
        val cart = cartRepository.findByToken(token)
            ?: throw VenuesException.ResourceNotFound("Hold token not found")

        cart.validatePlatformOwnership(platformId)

        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Hold has expired")
        }

        if (expectedSessionId != null && cart.sessionId != expectedSessionId) {
            throw VenuesException.ValidationFailure("Hold belongs to a different session")
        }

        if (cart.isEmpty()) {
            throw VenuesException.ValidationFailure("Hold is empty")
        }

        return CartValidationResult(
            token = cart.token,
            sessionId = cart.sessionId,
            expiresAt = cart.expiresAt.toString()
        )
    }
}

