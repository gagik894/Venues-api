package app.venues.user.repository

import app.venues.user.domain.UserFavoriteEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for UserFavoriteEvent entity operations.
 *
 * Provides database access methods for managing user favorite events.
 * Supports pagination, existence checks, and bulk operations.
 */
@Repository
interface UserFavoriteEventRepository : JpaRepository<UserFavoriteEvent, Long> {

    /**
     * Finds all favorite events for a specific user with pagination.
     *
     * @param userId ID of the user
     * @param pageable Pagination parameters
     * @return Page of favorite events
     */
    fun findByUserId(userId: Long, pageable: Pageable): Page<UserFavoriteEvent>

    /**
     * Finds all favorite events for a specific user.
     *
     * @param userId ID of the user
     * @return List of favorite events
     */
    fun findByUserId(userId: Long): List<UserFavoriteEvent>

    /**
     * Finds all users who favorited a specific event.
     *
     * @param eventId ID of the event
     * @return List of favorite event records
     */
    fun findByEventId(eventId: Long): List<UserFavoriteEvent>

    /**
     * Finds a specific favorite entry.
     *
     * @param userId ID of the user
     * @param eventId ID of the event
     * @return Favorite event entry if exists
     */
    fun findByUserIdAndEventId(userId: Long, eventId: Long): UserFavoriteEvent?

    /**
     * Checks if a user has favorited a specific event.
     *
     * @param userId ID of the user
     * @param eventId ID of the event
     * @return true if favorited
     */
    fun existsByUserIdAndEventId(userId: Long, eventId: Long): Boolean

    /**
     * Deletes a favorite entry.
     *
     * @param userId ID of the user
     * @param eventId ID of the event
     * @return Number of entries deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM UserFavoriteEvent f WHERE f.userId = :userId AND f.eventId = :eventId")
    fun deleteByUserIdAndEventId(userId: Long, eventId: Long): Int

    /**
     * Deletes all favorite events for a specific user.
     * Used when user deletes account.
     *
     * @param userId ID of the user
     * @return Number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM UserFavoriteEvent f WHERE f.userId = :userId")
    fun deleteByUserId(userId: Long): Int

    /**
     * Counts the number of times an event has been favorited.
     *
     * @param eventId ID of the event
     * @return Number of users who favorited this event
     */
    fun countByEventId(eventId: Long): Long

    /**
     * Counts the number of favorite events for a user.
     *
     * @param userId ID of the user
     * @return Number of favorite events
     */
    fun countByUserId(userId: Long): Long

    /**
     * Finds all users who favorited an event and have notifications enabled.
     *
     * @param eventId ID of the event
     * @return List of favorite event records with notifications enabled
     */
    fun findByEventIdAndNotificationsEnabled(
        eventId: Long,
        notificationsEnabled: Boolean = true
    ): List<UserFavoriteEvent>
}

