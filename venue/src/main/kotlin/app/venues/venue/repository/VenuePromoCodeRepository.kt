package app.venues.venue.repository

import app.venues.venue.domain.VenuePromoCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for VenuePromoCode entity.
 */
@Repository
interface VenuePromoCodeRepository : JpaRepository<VenuePromoCode, Long> {

    /**
     * Find promo code by venue and code
     */
    fun findByVenueIdAndCode(venueId: Long, code: String): Optional<VenuePromoCode>

    /**
     * Find all active promo codes for a venue
     */
    fun findByVenueIdAndIsActiveTrue(venueId: Long): List<VenuePromoCode>

    /**
     * Check if code exists for venue
     */
    fun existsByVenueIdAndCode(venueId: Long, code: String): Boolean
}

