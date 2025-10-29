package app.venues.venue.api

import app.venues.venue.api.dto.VenueBasicInfoDto

/**
 * Public API contract for Venue module.
 *
 * This is the Port in Hexagonal Architecture.
 * Defines the stable interface for venue data access.
 *
 * Implementation is provided by VenueService in the venue module.
 */
interface VenueApi {

    /**
     * Get basic venue information by ID.
     */
    fun getVenueBasicInfo(venueId: Long): VenueBasicInfoDto?

    /**
     * Get venue name by ID.
     */
    fun getVenueName(venueId: Long): String?

    /**
     * Get venue name with translation support.
     *
     * @param language Language code (e.g., "hy", "ru", "en")
     */
    fun getVenueNameTranslated(venueId: Long, language: String?): String?

    /**
     * Check if venue exists.
     */
    fun venueExists(venueId: Long): Boolean

    /**
     * Get venue owner ID.
     */
    fun getVenueOwnerId(venueId: Long): Long?
}

