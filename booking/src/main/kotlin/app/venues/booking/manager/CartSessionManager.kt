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
        // Customer cart expiration
        const val CART_EXPIRATION_MINUTES = 7 // Initial expiration for customer carts
        const val CART_EXTENSION_MINUTES = 5 // Extension on each action for customers
        const val CUSTOMER_MAX_CART_TTL_MINUTES = 20 // Max lifetime from creation for customers

        // Staff cart expiration
        const val STAFF_CART_EXPIRATION_MINUTES = 20 // Initial expiration for staff carts
        const val STAFF_CART_EXTENSION_MINUTES = 10 // Extension on each action for staff
        const val STAFF_MAX_CART_TTL_MINUTES = 30 // Max lifetime from creation for staff
    }

    /**
     * Find existing cart by token or create a new one.
     *
     * @param token Optional cart token to find existing cart
     * @param sessionId Event session ID
     * @param userId Optional user ID
     * @param isStaffCart Whether this is a staff cart (affects expiration timing)
     * @return Cart entity (existing or newly created)
     */
    fun findOrCreateCart(
        token: UUID?,
        sessionId: UUID,
        userId: UUID? = null,
        isStaffCart: Boolean = false,
        platformId: UUID? = null,
        customTtlSeconds: Long? = null
    ): Cart {
        val existingCart = token?.let { cartRepository.findByToken(it) }

        if (existingCart != null) {
            // Enforce platform binding if provided
            platformId?.let { existingCart.validatePlatformOwnership(it) }

            // If cart is expired or for different session, create a new one
            if (existingCart.isExpired() || existingCart.sessionId != sessionId) {
                return createNewCart(null, sessionId, userId, isStaffCart, platformId, customTtlSeconds)
            }
            return extendCartExpiration(existingCart, isStaffCart)
        }

        return createNewCart(null, sessionId, userId, isStaffCart, platformId, customTtlSeconds)
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
    fun getActiveCartWithItems(token: UUID, allowExpired: Boolean = false): Cart {
        val cart = cartRepository.findWithItemsByToken(token)
            ?: throw VenuesException.ResourceNotFound("Cart not found")

        if (!allowExpired && cart.isExpired()) {
            throw VenuesException.ValidationFailure("Cart has expired")
        }

        return cart
    }

    /**
     * Extends cart expiration time.
     * Uses staff or customer timing based on isStaffCart flag.
     */
    private fun extendCartExpiration(cart: Cart, isStaffCart: Boolean): Cart {
        val (extensionMinutes, initialExpirationMinutes, maxTtlMinutes) = if (isStaffCart) {
            Triple(
                STAFF_CART_EXTENSION_MINUTES.toLong(),
                STAFF_CART_EXPIRATION_MINUTES.toLong(),
                STAFF_MAX_CART_TTL_MINUTES.toLong()
            )
        } else {
            Triple(
                CART_EXTENSION_MINUTES.toLong(),
                CART_EXPIRATION_MINUTES.toLong(),
                CUSTOMER_MAX_CART_TTL_MINUTES.toLong()
            )
        }

        cart.extendExpiration(extensionMinutes, initialExpirationMinutes, maxTtlMinutes)
        return cartRepository.save(cart)
    }

    /**
     * Creates a new cart with appropriate expiration time.
     * Uses staff or customer initial expiration based on isStaffCart flag.
     */
    private fun createNewCart(
        token: UUID?,
        sessionId: UUID,
        userId: UUID?,
        isStaffCart: Boolean,
        platformId: UUID?,
        customTtlSeconds: Long?
    ): Cart {
        val initialExpirationSeconds = customTtlSeconds?.let {
            val capped = minOf(it, STAFF_MAX_CART_TTL_MINUTES * 60L) // cap at 30 minutes
            capped
        } ?: if (isStaffCart) {
            STAFF_CART_EXPIRATION_MINUTES * 60L
        } else {
            CART_EXPIRATION_MINUTES * 60L
        }
        
        val newCart = Cart(
            token = token ?: UUID.randomUUID(),
            userId = userId,
            sessionId = sessionId,
            platformId = platformId,
            expiresAt = Instant.now().plusSeconds(initialExpirationSeconds),
        )

        return cartRepository.save(newCart)
    }
}
