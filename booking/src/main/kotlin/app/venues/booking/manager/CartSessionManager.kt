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
        const val CART_EXPIRATION_MINUTES = 10
        const val CART_TOUCH_ADD_MINUTES = 5
        const val MAX_CART_TTL_MINUTES = 20 // Hard limit to prevent infinite holding
    }

    fun findOrCreateCart(token: UUID?, sessionId: UUID, userId: UUID? = null): Cart {
        val existingCart = token?.let { cartRepository.findByToken(it) }

        if (existingCart != null) {
            // If cart is expired or for different session, create a new one
            if (existingCart.isExpired() || existingCart.sessionId != sessionId) {
                return createNewCart(null, sessionId, userId)
            }
            return extendCartExpiration(existingCart)
        }

        return createNewCart(null, sessionId, userId)
    }

    fun getActiveCart(token: UUID): Cart {
        val cart = cartRepository.findByToken(token)
            ?: throw VenuesException.ResourceNotFound("Cart not found")

        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        return cart
    }

    /**
     * Retrieves an active cart with all child collections eagerly loaded.
     *
     * Uses @EntityGraph optimization to fetch cart + seats + gaItems + tables
     * in a single SQL query, preventing N+1 query problems.
     *
     * Use this method when you need to access cart items (e.g., for cart summary,
     * price calculations, or checkout operations).
     *
     * @param token The cart's public token.
     * @return Cart with all collections initialized.
     * @throws VenuesException.ResourceNotFound if cart doesn't exist.
     * @throws VenuesException.ValidationFailure if cart has expired.
     */
    fun getActiveCartWithItems(token: UUID): Cart {
        val cart = cartRepository.findWithItemsByToken(token)
            ?: throw VenuesException.ResourceNotFound("Cart not found")

        if (cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        return cart
    }

    fun touchCart(cart: Cart): Cart {
        // Extend expiration on activity to prevent session timeout while user is active
        cart.extendExpiration(CART_TOUCH_ADD_MINUTES.toLong(), MAX_CART_TTL_MINUTES.toLong())
        return cartRepository.save(cart)
    }

    private fun extendCartExpiration(cart: Cart): Cart {
        cart.extendExpiration(CART_EXPIRATION_MINUTES.toLong(), MAX_CART_TTL_MINUTES.toLong())
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
