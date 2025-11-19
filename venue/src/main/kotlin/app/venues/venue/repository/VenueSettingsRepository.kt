package app.venues.venue.repository

import app.venues.venue.domain.VenueSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for VenueSettings entity.
 *
 * Note: Always use lazy loading when accessing from Venue.
 * Never eagerly load settings when listing venues.
 *
 * The primary key is shared with Venue via @MapsId.
 */
@Repository
interface VenueSettingsRepository : JpaRepository<VenueSettings, UUID> {

    /**
     * Check if venue has settings configured.
     */
    fun existsByVenueId(venueId: UUID): Boolean

    /**
     * Find settings by venue ID.
     * This is the same as findById since VenueSettings uses @MapsId.
     */
    fun findByVenueId(venueId: UUID): VenueSettings?
}

