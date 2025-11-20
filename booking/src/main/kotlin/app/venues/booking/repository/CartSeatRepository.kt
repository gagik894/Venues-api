package app.venues.booking.repository

import app.venues.booking.domain.Cart
import app.venues.booking.domain.CartSeat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for CartSeat entity operations.
 */
@Repository
interface CartSeatRepository : JpaRepository<CartSeat, UUID> {

    /**
     * Find all seats in a cart
     */
    fun findByCart(cart: Cart): List<CartSeat>

    /**
     * Find all seats in a cart by token
     */
    @Query("SELECT cs FROM CartSeat cs WHERE cs.cart.token = :token")
    fun findByCartToken(token: UUID): List<CartSeat>

    /**
     * Check if seat already in cart for session
     */
    fun existsBySessionIdAndSeatId(sessionId: UUID, seatId: Long): Boolean

    /**
     * Get reserved seat IDs for session
     */
    @Query(
        """
        SELECT cs.seatId FROM CartSeat cs
        WHERE cs.sessionId = :sessionId
    """
    )
    fun findReservedSeatIdsBySession(sessionId: UUID): List<Long>

    /**
     * Get active (non-expired) reserved seat IDs for session
     */
    @Query(
        """
        SELECT cs.seatId FROM CartSeat cs
        JOIN cs.cart c
        WHERE cs.sessionId = :sessionId
        AND c.expiresAt > CURRENT_TIMESTAMP
    """
    )
    fun findActiveReservedSeatIdsBySession(sessionId: UUID): List<Long>

    /**
     * Delete all seats in a cart
     */
    fun deleteByCart(cart: Cart): Int
}

