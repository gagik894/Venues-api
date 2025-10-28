package app.venues.user.repository

import app.venues.user.domain.PromoCodeStatus
import app.venues.user.domain.UserPromoCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Repository for UserPromoCode entity operations.
 *
 * Provides database access methods for managing user promo codes.
 * Supports validation, usage tracking, and analytics queries.
 */
@Repository
interface UserPromoCodeRepository : JpaRepository<UserPromoCode, Long> {

    /**
     * Finds all promo codes for a specific user with pagination.
     *
     * @param userId ID of the user
     * @param pageable Pagination parameters
     * @return Page of promo codes
     */
    fun findByUserId(userId: Long, pageable: Pageable): Page<UserPromoCode>

    /**
     * Finds all promo codes for a specific user.
     *
     * @param userId ID of the user
     * @return List of promo codes
     */
    fun findByUserId(userId: Long): List<UserPromoCode>

    /**
     * Finds available (valid, not expired, not exhausted) promo codes for a user.
     *
     * @param userId ID of the user
     * @param status Status to filter by (default AVAILABLE)
     * @param now Current timestamp for expiration check
     * @return List of available promo codes
     */
    @Query(
        """
        SELECT p FROM UserPromoCode p 
        WHERE p.userId = :userId 
        AND p.status = :status
        AND (p.expiresAt IS NULL OR p.expiresAt > :now)
    """
    )
    fun findAvailableByUserId(
        userId: Long,
        status: PromoCodeStatus = PromoCodeStatus.AVAILABLE,
        now: Instant = Instant.now()
    ): List<UserPromoCode>

    /**
     * Finds a specific promo code for a user.
     *
     * @param userId ID of the user
     * @param promoCode The promo code string
     * @return Promo code entry if exists
     */
    fun findByUserIdAndPromoCode(userId: Long, promoCode: String): UserPromoCode?

    /**
     * Checks if a user has a specific promo code.
     *
     * @param userId ID of the user
     * @param promoCode The promo code string
     * @return true if exists
     */
    fun existsByUserIdAndPromoCode(userId: Long, promoCode: String): Boolean

    /**
     * Finds all users who have a specific promo code.
     *
     * @param promoCode The promo code string
     * @return List of user promo code entries
     */
    fun findByPromoCode(promoCode: String): List<UserPromoCode>

    /**
     * Counts how many users have used a specific promo code.
     *
     * @param promoCode The promo code string
     * @param status Status to filter by (default USED)
     * @return Number of users who used this code
     */
    fun countByPromoCodeAndStatus(promoCode: String, status: PromoCodeStatus = PromoCodeStatus.USED): Long

    /**
     * Finds expired promo codes that haven't been marked as expired yet.
     * Used for cleanup/maintenance jobs.
     *
     * @param now Current timestamp
     * @return List of expired promo codes
     */
    @Query(
        """
        SELECT p FROM UserPromoCode p 
        WHERE p.expiresAt < :now 
        AND p.status IN ('AVAILABLE', 'USED')
    """
    )
    fun findExpiredPromoCodes(now: Instant = Instant.now()): List<UserPromoCode>

    /**
     * Deletes all promo codes for a specific user.
     * Used when user deletes account.
     *
     * @param userId ID of the user
     * @return Number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM UserPromoCode p WHERE p.userId = :userId")
    fun deleteByUserId(userId: Long): Int

    /**
     * Counts total promo codes for a user by status.
     *
     * @param userId ID of the user
     * @param status Status to filter by
     * @return Number of promo codes
     */
    fun countByUserIdAndStatus(userId: Long, status: PromoCodeStatus): Long

    /**
     * Calculates total discount amount provided by a promo code.
     * Used for analytics and reporting.
     *
     * @param promoCode The promo code string
     * @return Total discount amount (sum of all usages)
     */
    @Query(
        """
        SELECT COALESCE(SUM(p.discountValue * p.timesUsed), 0) 
        FROM UserPromoCode p 
        WHERE p.promoCode = :promoCode
    """
    )
    fun calculateTotalDiscountByPromoCode(promoCode: String): java.math.BigDecimal
}

