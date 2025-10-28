package app.venues.venue.repository

import app.venues.venue.domain.VenueFollower
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for VenueFollower entity.
 */
@Repository
interface VenueFollowerRepository : JpaRepository<VenueFollower, Long> {

    /**
     * Find follower relationship
     */
    fun findByVenueIdAndUserId(venueId: Long, userId: Long): Optional<VenueFollower>

    /**
     * Check if user follows venue
     */
    fun existsByVenueIdAndUserId(venueId: Long, userId: Long): Boolean

    /**
     * Count followers for a venue
     */
    fun countByVenueId(venueId: Long): Long

    /**
     * Delete follower relationship
     */
    fun deleteByVenueIdAndUserId(venueId: Long, userId: Long)

    /**
     * Find all venues followed by a user
     */
    fun findByUserId(userId: Long): List<VenueFollower>
}

