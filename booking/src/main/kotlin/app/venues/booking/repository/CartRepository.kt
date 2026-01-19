package app.venues.booking.repository

import app.venues.booking.domain.Cart
import org.springframework.data.jpa.repository.EntityGraph
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
interface CartRepository : JpaRepository<Cart, UUID> {

    /**
     * Find cart by token (simple lookup, no child collections loaded).
     */
    fun findByToken(token: UUID): Cart?

    /**
     * Find cart by token with all child collections eagerly loaded.
     *
     * Uses @EntityGraph to prevent N+1 query problem by fetching
     * cart + seats + gaItems + tables in a single optimized SQL query
     * with LEFT JOIN FETCH for all collections.
     *
     * Use this method when you need to access cart items (e.g., checkout, cart summary).
     *
     * @param token The cart's public token.
     * @return Cart with all collections initialized, or null if not found.
     */
    @EntityGraph(attributePaths = ["seats", "gaItems", "tables"])
    fun findWithItemsByToken(token: UUID): Cart?

    /**
     * Find all carts for a user
     */
    fun findByUserId(userId: UUID): List<Cart>

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
     * Extend cart expiration
     */
    @Modifying
    @Query(
        """
        UPDATE Cart c
        SET c.expiresAt = :newExpiration,
            c.lastModifiedAt = :lastModifiedAt,
            c.version = c.version + 1
        WHERE c.token = :token AND c.version = :currentVersion
    """
    )
    fun extendExpiration(token: UUID, currentVersion: Long, newExpiration: Instant, lastModifiedAt: Instant): Int
}

