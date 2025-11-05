package app.venues.booking.repository

import app.venues.booking.domain.CartItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for CartItem entity operations.
 */
@Repository
interface CartItemRepository : JpaRepository<CartItem, Long> {

    /**
     * Find cart items by reservation token
     */
    fun findByReservationToken(token: UUID): List<CartItem>

    /**
     * Find expired cart items
     */
    fun findByExpiresAtBefore(now: Instant): List<CartItem>

    /**
     * Count active GA tickets for session + level
     */
    @Query(
        """
        SELECT COALESCE(SUM(ci.quantity), 0) FROM CartItem ci
        WHERE ci.sessionId = :sessionId
        AND ci.levelId = :levelId
        AND ci.expiresAt > :now
    """
    )
    fun countActiveGATicketsBySessionAndLevel(sessionId: Long, levelId: Long, now: Instant): Int

    /**
     * Delete by reservation token
     */
    fun deleteByReservationToken(token: UUID)
}

