package app.venues.user.repository

import app.venues.user.domain.UserBlockedUser
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for UserBlockedUser entity operations.
 *
 * Provides database access methods for managing user blocking relationships.
 * Supports bidirectional queries and moderation features.
 */
@Repository
interface UserBlockedUserRepository : JpaRepository<UserBlockedUser, UUID> {

    /**
     * Finds all users blocked by a specific user with pagination.
     *
     * @param blockingUserId ID of the user who blocked others
     * @param pageable Pagination parameters
     * @return Page of blocked user records
     */
    fun findByBlockingUserId(blockingUserId: UUID, pageable: Pageable): Page<UserBlockedUser>

    /**
     * Finds all users blocked by a specific user.
     *
     * @param blockingUserId ID of the user who blocked others
     * @return List of blocked user records
     */
    fun findByBlockingUserId(blockingUserId: UUID): List<UserBlockedUser>

    /**
     * Finds all users who blocked a specific user.
     * Used to check if current user is blocked by others.
     *
     * @param blockedUserId ID of the blocked user
     * @return List of blocking records
     */
    fun findByBlockedUserId(blockedUserId: UUID): List<UserBlockedUser>

    /**
     * Finds a specific blocking relationship.
     *
     * @param blockingUserId ID of the user who blocked
     * @param blockedUserId ID of the blocked user
     * @return Blocking record if exists
     */
    fun findByBlockingUserIdAndBlockedUserId(blockingUserId: UUID, blockedUserId: UUID): UserBlockedUser?

    /**
     * Checks if user A has blocked user B.
     *
     * @param blockingUserId ID of the user who might have blocked
     * @param blockedUserId ID of the potentially blocked user
     * @return true if blocking relationship exists
     */
    fun existsByBlockingUserIdAndBlockedUserId(blockingUserId: UUID, blockedUserId: UUID): Boolean

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
        WHERE (b.blockingUser.id = :userId1 AND b.blockedUser.id = :userId2)
        OR (b.blockingUser.id = :userId2 AND b.blockedUser.id = :userId1)
    """
    )
    fun existsBlockingBetween(userId1: UUID, userId2: UUID): Boolean

    /**
     * Deletes a blocking relationship (unblock).
     *
     * @param blockingUserId ID of the user who blocked
     * @param blockedUserId ID of the blocked user
     * @return Number of entries deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM UserBlockedUser b WHERE b.blockedUser.id = :blockingUserId AND b.blockedUser.id = :blockedUserId")
    fun deleteByBlockingUserIdAndBlockedUserId(blockingUserId: UUID, blockedUserId: UUID): Int

    /**
     * Deletes all blocking relationships created by a user.
     * Used when user deletes account.
     *
     * @param blockingUserId ID of the user
     * @return Number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM UserBlockedUser b WHERE b.blockedUser.id = :blockingUserId")
    fun deleteByBlockingUserId(blockingUserId: UUID): Int

    /**
     * Deletes all entries where a user is blocked by others.
     * Used when user deletes account.
     *
     * @param blockedUserId ID of the blocked user
     * @return Number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM UserBlockedUser b WHERE b.blockedUser.id = :blockedUserId")
    fun deleteByBlockedUserId(blockedUserId: UUID): Int

    /**
     * Counts how many users a specific user has blocked.
     *
     * @param blockingUserId ID of the user
     * @return Number of blocked users
     */
    fun countByBlockingUserId(blockingUserId: UUID): Long

    /**
     * Counts how many users have blocked a specific user.
     *
     * @param blockedUserId ID of the user
     * @return Number of users who blocked this user
     */
    fun countByBlockedUserId(blockedUserId: UUID): Long

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

