package app.venues.venue.repository

import app.venues.venue.domain.DayOfWeek
import app.venues.venue.domain.VenueSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for VenueSchedule entity operations.
 *
 * Provides database access methods for venue operating schedules.
 * Supports finding schedules by venue and day combinations.
 */
@Repository
interface VenueScheduleRepository : JpaRepository<VenueSchedule, Long> {

    /**
     * Find all schedules for a specific venue.
     *
     * @param venueId The venue ID
     * @return List of schedules for the venue
     */
    fun findByVenueId(venueId: Long): List<VenueSchedule>

    /**
     * Find schedule for a specific venue and day of week.
     *
     * @param venueId The venue ID
     * @param dayOfWeek The day of the week
     * @return Optional schedule for the venue and day
     */
    fun findByVenueIdAndDayOfWeek(venueId: Long, dayOfWeek: DayOfWeek): Optional<VenueSchedule>

    /**
     * Delete all schedules for a venue.
     * Used when a venue is deleted.
     *
     * @param venueId The venue ID
     */
    fun deleteByVenueId(venueId: Long)
}
