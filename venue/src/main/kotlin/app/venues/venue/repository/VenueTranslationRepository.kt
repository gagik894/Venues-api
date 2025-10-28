package app.venues.venue.repository

import app.venues.venue.domain.VenueTranslation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for VenueTranslation entity operations.
 *
 * Provides database access methods for venue translations.
 * Supports multi-language venue information management.
 */
@Repository
interface VenueTranslationRepository : JpaRepository<VenueTranslation, Long> {

    /**
     * Find all translations for a specific venue.
     *
     * @param venueId The venue ID
     * @return List of translations for the venue
     */
    fun findByVenueId(venueId: Long): List<VenueTranslation>

    /**
     * Find translation for a specific venue and language.
     *
     * @param venueId The venue ID
     * @param language The language code (e.g., "en", "fr", "hy")
     * @return Optional translation for the venue and language
     */
    fun findByVenueIdAndLanguage(venueId: Long, language: String): Optional<VenueTranslation>

    /**
     * Check if a translation exists for a venue and language.
     *
     * @param venueId The venue ID
     * @param language The language code
     * @return True if translation exists
     */
    fun existsByVenueIdAndLanguage(venueId: Long, language: String): Boolean

    /**
     * Find all translations for a specific language across all venues.
     * Useful for getting all venue information in a specific language.
     *
     * @param language The language code
     * @return List of translations in the specified language
     */
    fun findByLanguage(language: String): List<VenueTranslation>

    /**
     * Delete all translations for a venue.
     * Used when a venue is deleted.
     *
     * @param venueId The venue ID
     */
    fun deleteByVenueId(venueId: Long)
}
