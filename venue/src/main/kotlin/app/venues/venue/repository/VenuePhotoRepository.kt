package app.venues.venue.repository

import app.venues.venue.domain.VenuePhoto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository interface for VenuePhoto entity operations.
 *
 * Provides database access methods for venue photos.
 * Supports photo management with display ordering.
 */
@Repository
interface VenuePhotoRepository : JpaRepository<VenuePhoto, Long> {

    /**
     * Find all photos for a specific venue, ordered by display order.
     *
     * @param venueId The venue ID
     * @return List of photos ordered by display order (ascending)
     */
    fun findByVenueIdOrderByDisplayOrderAsc(venueId: Long): List<VenuePhoto>

    /**
     * Find all photos uploaded by a specific user.
     *
     * @param userId The user ID
     * @return List of photos uploaded by the user
     */
    fun findByUserId(userId: Long): List<VenuePhoto>

    /**
     * Find all photos for a venue uploaded by a specific user.
     *
     * @param venueId The venue ID
     * @param userId The user ID
     * @return List of photos for the venue uploaded by the user
     */
    fun findByVenueIdAndUserId(venueId: Long, userId: Long): List<VenuePhoto>

    /**
     * Count total photos for a venue.
     *
     * @param venueId The venue ID
     * @return Number of photos for the venue
     */
    fun countByVenueId(venueId: Long): Long

    /**
     * Delete all photos for a venue.
     * Used when a venue is deleted.
     *
     * @param venueId The venue ID
     */
    fun deleteByVenueId(venueId: Long)

    /**
     * Delete all photos uploaded by a user.
     * Used when a user is deleted.
     *
     * @param userId The user ID
     */
    fun deleteByUserId(userId: Long)
}
