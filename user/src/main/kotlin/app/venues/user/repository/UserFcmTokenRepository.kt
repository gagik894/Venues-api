package app.venues.user.repository

import app.venues.user.domain.UserFcmToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for UserFcmToken entity operations.
 *
 * Provides database access methods for managing FCM tokens.
 * Supports queries for user tokens, platform filtering, and cleanup operations.
 */
@Repository
interface UserFcmTokenRepository : JpaRepository<UserFcmToken, Long> {

    /**
     * Finds all FCM tokens for a specific user.
     *
     * @param userId ID of the user
     * @return List of FCM tokens belonging to the user
     */
    fun findByUserId(UUID: Long): List<UserFcmToken>

    /**
     * Finds all FCM tokens for a specific user and platform.
     *
     * @param userId ID of the user
     * @param platform Platform name (e.g., "android", "ios")
     * @return List of FCM tokens for the user on the specified platform
     */
    fun findByUserIdAndPlatform(userId: UUID, platform: String): List<UserFcmToken>

    /**
     * Finds a specific FCM token.
     *
     * @param token The FCM token string
     * @return FCM token entity if found
     */
    fun findByToken(token: String): UserFcmToken?

    /**
     * Checks if a specific token exists.
     *
     * @param token The FCM token string
     * @return true if token exists
     */
    fun existsByToken(token: String): Boolean

    /**
     * Deletes all FCM tokens for a specific user.
     * Useful when user logs out from all devices or deletes account.
     *
     * @param userId ID of the user
     * @return Number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM UserFcmToken t WHERE t.user.id = :userId")
    fun deleteByUserId(userId: UUID): Int

    /**
     * Deletes a specific FCM token.
     *
     * @param token The FCM token string
     * @return Number of tokens deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM UserFcmToken t WHERE t.token = :token")
    fun deleteByToken(token: String): Int

    /**
     * Finds stale FCM tokens that haven't been used in a specified time period.
     * Used for cleanup operations.
     *
     * @param cutoffDate Tokens not used since this date are considered stale
     * @return List of stale FCM tokens
     */
    @Query(
        """
        SELECT t FROM UserFcmToken t 
        WHERE t.lastUsedAt < :cutoffDate 
        OR (t.lastUsedAt IS NULL AND t.createdAt < :cutoffDate)
    """
    )
    fun findStaleTokens(cutoffDate: Instant): List<UserFcmToken>

    /**
     * Counts the number of FCM tokens for a specific user.
     *
     * @param userId ID of the user
     * @return Number of tokens
     */
    fun countByUserId(userId: UUID): Int
}

