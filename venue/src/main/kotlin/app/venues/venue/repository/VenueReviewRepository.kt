package app.venues.venue.repository

import app.venues.venue.domain.VenueReview
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for VenueReview entity operations.
 *
 * Provides database access methods for venue reviews and ratings.
 * Supports moderation and statistical queries.
 */
@Repository
interface VenueReviewRepository : JpaRepository<VenueReview, Long> {

    /**
     * Find all reviews for a specific venue (paginated).
     *
     * @param venueId The venue ID
     * @param pageable Pagination parameters
     * @return Page of reviews for the venue
     */
    fun findByVenueId(venueId: Long, pageable: Pageable): Page<VenueReview>

    /**
     * Find all non-moderated reviews for a specific venue (paginated).
     * Used for public display - only shows approved/unmoderated content.
     *
     * @param venueId The venue ID
     * @param pageable Pagination parameters
     * @return Page of non-moderated reviews for the venue
     */
    fun findByVenueIdAndIsModeratedFalse(venueId: Long, pageable: Pageable): Page<VenueReview>

    /**
     * Find review by venue and user.
     * Each user can only have one review per venue.
     *
     * @param venueId The venue ID
     * @param userId The user ID
     * @return Optional review by the user for the venue
     */
    fun findByVenueIdAndUserId(venueId: Long, userId: Long): Optional<VenueReview>

    /**
     * Find all reviews by a specific user.
     *
     * @param userId The user ID
     * @return List of reviews by the user
     */
    fun findByUserId(userId: Long): List<VenueReview>

    /**
     * Check if a user has already reviewed a venue.
     *
     * @param venueId The venue ID
     * @param userId The user ID
     * @return True if user has reviewed the venue
     */
    fun existsByVenueIdAndUserId(venueId: Long, userId: Long): Boolean

    /**
     * Count total reviews for a venue.
     *
     * @param venueId The venue ID
     * @return Number of reviews for the venue
     */
    fun countByVenueId(venueId: Long): Long

    /**
     * Count non-moderated reviews for a venue.
     * Used for public statistics.
     *
     * @param venueId The venue ID
     * @return Number of non-moderated reviews for the venue
     */
    fun countByVenueIdAndIsModeratedFalse(venueId: Long): Long

    /**
     * Calculate average rating for a venue.
     *
     * @param venueId The venue ID
     * @return Average rating or null if no reviews
     */
    @Query("SELECT AVG(r.rating) FROM VenueReview r WHERE r.venue.id = :venueId AND r.isModerated = false")
    fun findAverageRatingByVenueId(venueId: Long): Double?

    /**
     * Find reviews that need moderation (moderated = false).
     * Used by admin interfaces.
     *
     * @param pageable Pagination parameters
     * @return Page of reviews needing moderation
     */
    fun findByIsModeratedFalse(pageable: Pageable): Page<VenueReview>

    /**
     * Delete all reviews for a venue.
     * Used when a venue is deleted.
     *
     * @param venueId The venue ID
     */
    fun deleteByVenueId(venueId: Long)

    /**
     * Delete all reviews by a user.
     * Used when a user is deleted.
     *
     * @param userId The user ID
     */
    fun deleteByUserId(userId: Long)
}
