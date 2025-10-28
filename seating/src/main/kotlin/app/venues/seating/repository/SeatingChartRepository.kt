package app.venues.seating.repository

import app.venues.seating.domain.SeatingChart
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for SeatingChart entity operations.
 */
@Repository
interface SeatingChartRepository : JpaRepository<SeatingChart, Long> {

    /**
     * Find seating charts by venue ID
     */
    fun findByVenueId(venueId: Long, pageable: Pageable): Page<SeatingChart>

    /**
     * Find seating chart by venue and name
     */
    fun findByVenueIdAndName(venueId: Long, name: String): SeatingChart?

    /**
     * Check if chart name exists for venue
     */
    fun existsByVenueIdAndName(venueId: Long, name: String): Boolean

    /**
     * Count charts by venue
     */
    fun countByVenueId(venueId: Long): Long
}

