package app.venues.booking.repository

import app.venues.booking.domain.Cart
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for Cart entity operations.
 */
@Repository
interface CartRepository : JpaRepository<Cart, Long> {

    /**
     * Find cart by token
     */
    fun findByToken(token: UUID): Cart?

    /**
     * Find all carts for a user
     */
    fun findByUserId(userId: Long): List<Cart>

    /**
     * Find expired carts
     */
    fun findByExpiresAtBefore(now: Instant): List<Cart>

    /**
     * Delete expired carts
     */
    @Modifying
    @Query("DELETE FROM Cart c WHERE c.expiresAt < :now")
    fun deleteExpired(now: Instant): Int

    /**
     * Extend cart expiration (touch session)
     */
    @Modifying
    @Query(
        """
        UPDATE Cart c
        SET c.expiresAt = :newExpiration,
            c.lastActivityAt = :now
        WHERE c.token = :token
    """
    )
    fun extendExpiration(token: UUID, newExpiration: Instant, now: Instant): Int
}

