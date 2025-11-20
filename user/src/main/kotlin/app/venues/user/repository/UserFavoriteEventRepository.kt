package app.venues.user.repository

import app.venues.user.domain.UserFavoriteEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

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
    fun findByUserId(userId: UUID, pageable: Pageable): Page<UserFavoriteEvent>

    /**
     * Finds all favorite events for a specific user.
     *
     * @param userId ID of the user
     * @return List of favorite events
     */
    fun findByUserId(userId: UUID): List<UserFavoriteEvent>

    /**
     * Finds all users who favorited a specific event.
     *
     * @param eventId ID of the event
     * @return List of favorite event records
     */
    fun findByEventId(eventId: UUID): List<UserFavoriteEvent>

    /**
     * Finds a specific favorite entry.
     *
     * @param userId ID of the user
     * @param eventId ID of the event
     * @return Favorite event entry if exists
     */
    fun findByUserIdAndEventId(userId: UUID, eventId: UUID): UserFavoriteEvent?

    /**
     * Checks if a user has favorited a specific event.
     *
     * @param userId ID of the user
     * @param eventId ID of the event
     * @return true if favorited
     */
    fun existsByUserIdAndEventId(userId: UUID, eventId: UUID): Boolean

    /**
     * Deletes a favorite entry.
     *
     * @param userId ID of the user
     * @param eventId ID of the event
     * @return Number of entries deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM UserFavoriteEvent f WHERE f.user.id = :userId AND f.eventId = :eventId")
    fun deleteByUserIdAndEventId(userId: UUID, eventId: UUID): Int

    /**
     * Deletes all favorite events for a specific user.
     * Used when user deletes account.
     *
     * @param userId ID of the user
     * @return Number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM UserFavoriteEvent f WHERE f.user.id = :userId")
    fun deleteByUserId(userId: UUID): Int

    /**
     * Counts the number of times an event has been favorited.
     *
     * @param eventId ID of the event
     * @return Number of users who favorited this event
     */
    fun countByEventId(eventId: UUID): Long

    /**
     * Counts the number of favorite events for a user.
     *
     * @param userId ID of the user
     * @return Number of favorite events
     */
    fun countByUserId(userId: UUID): Long

    /**
     * Finds all users who favorited an event and have notifications enabled.
     *
     * @param eventId ID of the event
     * @return List of favorite event records with notifications enabled
     */
    fun findByEventIdAndNotificationsEnabled(
        eventId: UUID,
        notificationsEnabled: Boolean = true
    ): List<UserFavoriteEvent>
}

