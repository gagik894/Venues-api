package app.venues.user.repository

import app.venues.user.domain.UserBlockedUser
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for UserBlockedUser entity operations.
 *
 * Provides database access methods for managing user blocking relationships.
 * Supports bidirectional queries and moderation features.
 */
@Repository
interface UserBlockedUserRepository : JpaRepository<UserBlockedUser, Long> {

    /**
     * Finds all users blocked by a specific user with pagination.
     *
     * @param blockingUserId ID of the user who blocked others
     * @param pageable Pagination parameters
     * @return Page of blocked user records
     */
    fun findByBlockingUserId(blockingUserId: Long, pageable: Pageable): Page<UserBlockedUser>

    /**
     * Finds all users blocked by a specific user.
     *
     * @param blockingUserId ID of the user who blocked others
     * @return List of blocked user records
     */
    fun findByBlockingUserId(blockingUserId: Long): List<UserBlockedUser>

    /**
     * Finds all users who blocked a specific user.
     * Used to check if current user is blocked by others.
     *
     * @param blockedUserId ID of the blocked user
     * @return List of blocking records
     */
    fun findByBlockedUserId(blockedUserId: Long): List<UserBlockedUser>

    /**
     * Finds a specific blocking relationship.
     *
     * @param blockingUserId ID of the user who blocked
     * @param blockedUserId ID of the blocked user
     * @return Blocking record if exists
     */
    fun findByBlockingUserIdAndBlockedUserId(blockingUserId: Long, blockedUserId: Long): UserBlockedUser?

    /**
     * Checks if user A has blocked user B.
     *
     * @param blockingUserId ID of the user who might have blocked
     * @param blockedUserId ID of the potentially blocked user
     * @return true if blocking relationship exists
     */
    fun existsByBlockingUserIdAndBlockedUserId(blockingUserId: Long, blockedUserId: Long): Boolean

    /**
     * Checks if there's any blocking relationship between two users (bidirectional).
     * Returns true if A blocked B OR B blocked A.
     *
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return true if any blocking relationship exists
     */
    @Query(
        """
        SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END 
        FROM UserBlockedUser b 
        WHERE (b.blockingUserId = :userId1 AND b.blockedUserId = :userId2)
        OR (b.blockingUserId = :userId2 AND b.blockedUserId = :userId1)
    """
    )
    fun existsBlockingBetween(userId1: Long, userId2: Long): Boolean

    /**
     * Deletes a blocking relationship (unblock).
     *
     * @param blockingUserId ID of the user who blocked
     * @param blockedUserId ID of the blocked user
     * @return Number of entries deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM UserBlockedUser b WHERE b.blockingUserId = :blockingUserId AND b.blockedUserId = :blockedUserId")
    fun deleteByBlockingUserIdAndBlockedUserId(blockingUserId: Long, blockedUserId: Long): Int

    /**
     * Deletes all blocking relationships created by a user.
     * Used when user deletes account.
     *
     * @param blockingUserId ID of the user
     * @return Number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM UserBlockedUser b WHERE b.blockingUserId = :blockingUserId")
    fun deleteByBlockingUserId(blockingUserId: Long): Int

    /**
     * Deletes all entries where a user is blocked by others.
     * Used when user deletes account.
     *
     * @param blockedUserId ID of the blocked user
     * @return Number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM UserBlockedUser b WHERE b.blockedUserId = :blockedUserId")
    fun deleteByBlockedUserId(blockedUserId: Long): Int

    /**
     * Counts how many users a specific user has blocked.
     *
     * @param blockingUserId ID of the user
     * @return Number of blocked users
     */
    fun countByBlockingUserId(blockingUserId: Long): Long

    /**
     * Counts how many users have blocked a specific user.
     *
     * @param blockedUserId ID of the user
     * @return Number of users who blocked this user
     */
    fun countByBlockedUserId(blockedUserId: Long): Long

    /**
     * Finds all blocking records with abuse-related reasons.
     * Used for moderation and reporting.
     *
     * @param reasons List of abuse-related reasons
     * @param pageable Pagination parameters
     * @return Page of blocking records
     */
    fun findByBlockReasonIn(reasons: List<String>, pageable: Pageable): Page<UserBlockedUser>
}

