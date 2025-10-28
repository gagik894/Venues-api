package app.venues.venue.repository

import app.venues.venue.domain.VenueReview
import app.venues.venue.domain.VenueSchedule
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.util.*

/**
 * Repository for VenueSchedule entity.
 */
@Repository
interface VenueScheduleRepository : JpaRepository<VenueSchedule, Long> {

    /**
     * Find all schedules for a venue
     */
    fun findByVenueId(venueId: Long): List<VenueSchedule>

    /**
     * Find schedule for specific day
     */
    fun findByVenueIdAndDayOfWeek(venueId: Long, dayOfWeek: DayOfWeek): Optional<VenueSchedule>

    /**
     * Delete all schedules for a venue
     */
    fun deleteByVenueId(venueId: Long)
}

/**
 * Repository for VenueReview entity.
 */
@Repository
interface VenueReviewRepository : JpaRepository<VenueReview, Long> {

    /**
     * Find all reviews for a venue
     */
    fun findByVenueIdAndIsModeratedFalse(venueId: Long, pageable: Pageable): Page<VenueReview>

    /**
     * Find review by user and venue
     */
    fun findByVenueIdAndUserId(venueId: Long, userId: Long): Optional<VenueReview>

    /**
     * Check if user has already reviewed this venue
     */
    fun existsByVenueIdAndUserId(venueId: Long, userId: Long): Boolean

    /**
     * Calculate average rating for a venue
     */
    @Query("SELECT AVG(r.rating) FROM VenueReview r WHERE r.venue.id = :venueId AND r.isModerated = false")
    fun calculateAverageRating(@Param("venueId") venueId: Long): Double?

    /**
     * Count reviews for a venue
     */
    fun countByVenueIdAndIsModeratedFalse(venueId: Long): Long
}

