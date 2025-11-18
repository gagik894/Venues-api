package app.venues.seating.repository

import app.venues.seating.domain.SeatingChart
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for SeatingChart entity operations.
 */
@Repository
interface SeatingChartRepository : JpaRepository<SeatingChart, UUID> {

    /**
     * Find seating charts by venue ID
     */
    fun findByVenueId(venueId: UUID, pageable: Pageable): Page<SeatingChart>

    /**
     * Find seating chart by venue and name
     */
    fun findByVenueIdAndName(venueId: UUID, name: String): SeatingChart?

    /**
     * Check if chart name exists for venue
     */
    fun existsByVenueIdAndName(venueId: UUID, name: String): Boolean
}

