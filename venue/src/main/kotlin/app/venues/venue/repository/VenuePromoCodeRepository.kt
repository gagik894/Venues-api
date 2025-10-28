package app.venues.venue.repository

import app.venues.venue.domain.VenuePromoCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository interface for VenuePromoCode entity operations.
 *
 * Provides database access methods for venue promotional codes.
 * Supports validation and usage tracking.
 */
@Repository
interface VenuePromoCodeRepository : JpaRepository<VenuePromoCode, Long> {

    /**
     * Find all active promo codes for a specific venue.
     *
     * @param venueId The venue ID
     * @return List of active promo codes for the venue
     */
    fun findByVenueIdAndIsActiveTrue(venueId: Long): List<VenuePromoCode>

    /**
     * Find all promo codes for a specific venue (including inactive).
     *
     * @param venueId The venue ID
     * @return List of all promo codes for the venue
     */
    fun findByVenueId(venueId: Long): List<VenuePromoCode>

    /**
     * Find promo code by venue and code.
     *
     * @param venueId The venue ID
     * @param code The promo code
     * @return Optional promo code
     */
    fun findByVenueIdAndCode(venueId: Long, code: String): Optional<VenuePromoCode>

    /**
     * Find promo code by code (across all venues).
     * Used for global code validation.
     *
     * @param code The promo code
     * @return Optional promo code
     */
    fun findByCode(code: String): Optional<VenuePromoCode>

    /**
     * Check if a promo code exists for a venue.
     *
     * @param venueId The venue ID
     * @param code The promo code
     * @return True if code exists for the venue
     */
    fun existsByVenueIdAndCode(venueId: Long, code: String): Boolean

    /**
     * Find all valid (active, not expired, under usage limit) promo codes for a venue.
     *
     * @param venueId The venue ID
     * @param currentTime Current timestamp
     * @return List of valid promo codes
     */
    @Query(
        """
        SELECT p FROM VenuePromoCode p 
        WHERE p.venue.id = :venueId 
        AND p.isActive = true 
        AND (p.expiresAt IS NULL OR p.expiresAt > :currentTime)
        AND (p.maxUsageCount IS NULL OR p.currentUsageCount < p.maxUsageCount)
    """
    )
    fun findValidPromoCodesByVenueId(venueId: Long, currentTime: Instant): List<VenuePromoCode>

    /**
     * Find a valid promo code by venue and code.
     *
     * @param venueId The venue ID
     * @param code The promo code
     * @param currentTime Current timestamp
     * @return Optional valid promo code
     */
    @Query(
        """
        SELECT p FROM VenuePromoCode p 
        WHERE p.venue.id = :venueId 
        AND p.code = :code
        AND p.isActive = true 
        AND (p.expiresAt IS NULL OR p.expiresAt > :currentTime)
        AND (p.maxUsageCount IS NULL OR p.currentUsageCount < p.maxUsageCount)
    """
    )
    fun findValidPromoCodeByVenueIdAndCode(venueId: Long, code: String, currentTime: Instant): Optional<VenuePromoCode>

    /**
     * Find expired promo codes.
     * Used for cleanup operations.
     *
     * @param currentTime Current timestamp
     * @return List of expired promo codes
     */
    @Query("SELECT p FROM VenuePromoCode p WHERE p.expiresAt IS NOT NULL AND p.expiresAt <= :currentTime")
    fun findExpiredPromoCodes(currentTime: Instant): List<VenuePromoCode>

    /**
     * Count active promo codes for a venue.
     *
     * @param venueId The venue ID
     * @return Number of active promo codes for the venue
     */
    fun countByVenueIdAndIsActiveTrue(venueId: Long): Long

    /**
     * Delete all promo codes for a venue.
     * Used when a venue is deleted.
     *
     * @param venueId The venue ID
     */
    fun deleteByVenueId(venueId: Long)
}
