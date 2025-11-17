package app.venues.venue.repository

import app.venues.venue.domain.VenuePhoto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

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
    fun findByVenueIdOrderByDisplayOrderAsc(venueId: UUID): List<VenuePhoto>

    /**
     * Find all photos uploaded by a specific user.
     *
     * @param userId The user ID
     * @return List of photos uploaded by the user
     */
    fun findByUserId(userId: UUID): List<VenuePhoto>

    /**
     * Find all photos for a venue uploaded by a specific user.
     *
     * @param venueId The venue ID
     * @param userId The user ID
     * @return List of photos for the venue uploaded by the user
     */
    fun findByVenueIdAndUserId(venueId: UUID, userId: UUID): List<VenuePhoto>

    /**
     * Count total photos for a venue.
     *
     * @param venueId The venue ID
     * @return Number of photos for the venue
     */
    fun countByVenueId(venueId: UUID): Long

    /**
     * Delete all photos for a venue.
     * Used when a venue is deleted.
     *
     * @param venueId The venue ID
     */
    fun deleteByVenueId(venueId: UUID)

    /**
     * Delete all photos uploaded by a user.
     * Used when a user is deleted.
     *
     * @param userId The user ID
     */
    fun deleteByUserId(userId: UUID)
}
