package app.venues.venue.repository

import app.venues.venue.domain.VenueTranslation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for VenueTranslation entity.
 */
@Repository
interface VenueTranslationRepository : JpaRepository<VenueTranslation, Long> {

    /**
     * Find translation for specific venue and language
     */
    fun findByVenueIdAndLanguage(venueId: Long, language: String): Optional<VenueTranslation>

    /**
     * Find all translations for a venue
     */
    fun findByVenueId(venueId: Long): List<VenueTranslation>

    /**
     * Check if translation exists
     */
    fun existsByVenueIdAndLanguage(venueId: Long, language: String): Boolean

    /**
     * Delete all translations for a venue
     */
    fun deleteByVenueId(venueId: Long)
}

