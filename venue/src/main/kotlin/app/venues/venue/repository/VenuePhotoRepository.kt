package app.venues.venue.repository

import app.venues.venue.domain.VenuePhoto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for VenuePhoto entity.
 */
@Repository
interface VenuePhotoRepository : JpaRepository<VenuePhoto, Long> {

    /**
     * Find all photos for a venue (ordered by display order)
     */
    fun findByVenueIdOrderByDisplayOrderAsc(venueId: Long): List<VenuePhoto>

    /**
     * Find photos uploaded by a user
     */
    fun findByUserId(userId: Long): List<VenuePhoto>

    /**
     * Delete all photos for a venue
     */
    fun deleteByVenueId(venueId: Long)
}

