package app.venues.booking.repository

import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for CartItem entity operations.
 */
@Repository
interface CartItemRepository : JpaRepository<CartItem, UUID> {

    /**
     * Find all items in a cart
     */
    fun findByCart(cart: Cart): List<CartItem>

    /**
     * Find specific GA item in cart by level
     */
    fun findByCartAndLevelId(cart: Cart, levelId: Long): CartItem?

    /**
     * Find all items in a cart by token
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.token = :token")
    fun findByCartToken(token: UUID): List<CartItem>

    /**
     * Count active GA tickets reserved for session/level
     */
    @Query(
        """
        SELECT COALESCE(SUM(ci.quantity), 0) FROM CartItem ci
        JOIN ci.cart c
        WHERE ci.sessionId = :sessionId
        AND ci.levelId = :levelId
        AND c.expiresAt > CURRENT_TIMESTAMP
    """
    )
    fun countActiveGATicketsBySessionAndLevel(sessionId: Long, levelId: Long): Int

    /**
     * Delete all items in a cart
     */
    fun deleteByCart(cart: Cart): Int
}

