package app.venues.venue.repository

import app.venues.venue.domain.VenueFollower
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for VenueFollower entity operations.
 *
 * Provides database access methods for venue following/follower relationships.
 * Supports notification preferences and engagement tracking.
 */
@Repository
interface VenueFollowerRepository : JpaRepository<VenueFollower, Long> {

    /**
     * Find all followers of a specific venue.
     *
     * @param venueId The venue ID
     * @return List of followers for the venue
     */
    fun findByVenueId(venueId: UUID): List<VenueFollower>

    /**
     * Find all venues followed by a specific user.
     *
     * @param userId The user ID
     * @return List of venues followed by the user
     */
    fun findByUserId(userId: UUID): List<VenueFollower>

    /**
     * Find followers of a venue who have notifications enabled.
     * Used for sending venue updates and notifications.
     *
     * @param venueId The venue ID
     * @return List of followers with notifications enabled
     */
    fun findByVenueIdAndNotificationsEnabledTrue(venueId: UUID): List<VenueFollower>

    /**
     * Check if a user is following a venue.
     *
     * @param venueId The venue ID
     * @param userId The user ID
     * @return True if user is following the venue
     */
    fun existsByVenueIdAndUserId(venueId: UUID, userId: UUID): Boolean

    /**
     * Count total followers for a venue.
     *
     * @param venueId The venue ID
     * @return Number of followers for the venue
     */
    fun countByVenueId(venueId: UUID): Long

    /**
     * Count venues followed by a user.
     *
     * @param userId The user ID
     * @return Number of venues followed by the user
     */
    fun countByUserId(userId: UUID): Long

    /**
     * Delete a follow relationship.
     *
     * @param venueId The venue ID
     * @param userId The user ID
     */
    @Modifying
    @Query("DELETE FROM VenueFollower vf WHERE vf.venue.id = :venueId AND vf.userId = :userId")
    fun deleteByVenueIdAndUserId(venueId: UUID, userId: UUID)

    /**
     * Delete all followers for a venue.
     * Used when a venue is deleted.
     *
     * @param venueId The venue ID
     */
    fun deleteByVenueId(venueId: UUID)

    /**
     * Delete all follows by a user.
     * Used when a user is deleted.
     *
     * @param userId The user ID
     */
    fun deleteByUserId(userId: UUID)

    /**
     * Update notification preferences for a follow relationship.
     *
     * @param venueId The venue ID
     * @param userId The user ID
     * @param notificationsEnabled Whether notifications should be enabled
     */
    @Modifying
    @Query("UPDATE VenueFollower vf SET vf.notificationsEnabled = :notificationsEnabled WHERE vf.venue.id = :venueId AND vf.userId = :userId")
    fun updateNotificationPreferences(venueId: UUID, userId: UUID, notificationsEnabled: Boolean)
}
