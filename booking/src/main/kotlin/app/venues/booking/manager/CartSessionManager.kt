package app.venues.booking.manager

import app.venues.booking.domain.Cart
import app.venues.booking.repository.CartRepository
import app.venues.common.exception.VenuesException
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

/**
 * Manages cart session lifecycle.
 * Handles cart creation, lookup, expiration checks, and activity tracking.
 */
@Component
class CartSessionManager(
    private val cartRepository: CartRepository
) {
    companion object {
        const val CART_EXPIRATION_MINUTES = 15
    }

    fun findOrCreateCart(token: UUID?, sessionId: UUID, userId: UUID? = null): Cart {
        val existingCart = token?.let { cartRepository.findByToken(it) }

        if (existingCart != null) {
            validateCartSession(existingCart, sessionId)
            return extendCartExpiration(existingCart)
        }

        return createNewCart(token, sessionId, userId)
    }

    fun getActiveCart(token: UUID): Cart {
        val cart = cartRepository.findByToken(token)
            ?: throw VenuesException.ResourceNotFound("Cart not found")

        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        return cart
    }

    fun touchCart(cart: Cart): Cart {
        cart.touch()
        return cartRepository.save(cart)
    }

    private fun validateCartSession(cart: Cart, sessionId: UUID) {
        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired. Please start a new cart.")
        }

        if (cart.sessionId != sessionId) {
            throw VenuesException.ValidationFailure(
                "Cannot add items from different event sessions to the same cart"
            )
        }
    }

    private fun extendCartExpiration(cart: Cart): Cart {
        cart.extendExpiration(CART_EXPIRATION_MINUTES.toLong())
        return cartRepository.save(cart)
    }

    private fun createNewCart(token: UUID?, sessionId: UUID, userId: UUID?): Cart {
        val newCart = Cart(
            token = token ?: UUID.randomUUID(),
            userId = userId,
            sessionId = sessionId,
            expiresAt = Instant.now().plusSeconds(CART_EXPIRATION_MINUTES * 60L),
        )

        return cartRepository.save(newCart)
    }
}
