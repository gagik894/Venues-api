package app.venues.venue.api

import app.venues.venue.api.dto.VenueBasicInfoDto
import java.util.*

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
    fun getVenueBasicInfo(venueId: UUID): VenueBasicInfoDto?

    /**
     * Get venue name by ID.
     */
    fun getVenueName(venueId: UUID): String

    /**
     * Get venue name with translation support.
     *
     * @param language Language code (e.g., "hy", "ru", "en")
     */
    fun getVenueNameTranslated(venueId: UUID, language: String?): String?

    /**
     * Check if venue exists.
     */
    fun venueExists(venueId: UUID): Boolean

    /**
     * Get venue's organization ID for permission checking.
     *
     * @param venueId Venue UUID
     * @return Organization UUID or null if venue has no organization
     */
    fun getVenueOrganizationId(venueId: UUID): UUID?

    /**
     * Get venue names in batch (for performance optimization).
     * Returns a map of venueId to venue name.
     *
     * @param venueIds Set of venue IDs to fetch
     * @param language Optional language code for translations
     */
    fun getVenueNamesBatch(venueIds: Set<UUID>, language: String? = null): Map<UUID, String>

    /**
     * Validate a promo code for a venue.
     *
     * @param venueId The venue ID
     * @param code The promo code string
     * @return PromoCodeDto if valid, null if invalid or not found
     */
    fun validatePromoCode(venueId: UUID, code: String): app.venues.venue.api.dto.PromoCodeDto?

    /**
     * Redeem a promo code (increment usage).
     * Should be called when a booking is confirmed.
     *
     * @param venueId The venue ID
     * @param code The promo code string
     * @throws RuntimeException if redemption fails (e.g. limit reached)
     */
    fun redeemPromoCode(venueId: UUID, code: String)

    /**
     * Get venue basic info in batch.
     * Returns a map of venueId to VenueBasicInfoDto.
     */
    fun getVenueBasicInfoBatch(venueIds: Set<UUID>): Map<UUID, VenueBasicInfoDto>
}

