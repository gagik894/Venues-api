package app.venues.venue.repository

import app.venues.venue.domain.VenuePromoCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository interface for VenuePromoCode entity operations.
 *
 * Provides database access methods for venue promotional codes.
 * Supports validation and atomic usage tracking to prevent race conditions.
 */
@Repository
interface VenuePromoCodeRepository : JpaRepository<VenuePromoCode, UUID> {

    // ============================================================================================
    // REGION: Secure Management (Admin Dashboard)
    // Use these methods for CRUD operations in the Venue Manager Dashboard.
    // ============================================================================================

    /**
     * Securely retrieves a promo code by ID, verifying venue ownership in the database layer.
     * Use this instead of standard [findById] to prevent IDOR attacks.
     */
    fun findByIdAndVenueId(id: UUID, venueId: UUID): Optional<VenuePromoCode>

    /**
     * Performs a case-insensitive fuzzy search for promo codes within a venue.
     */
    fun findByVenueIdAndCodeContainingIgnoreCase(venueId: UUID, code: String): List<VenuePromoCode>

    /**
     * Retrieves all active and inactive promo codes for a specific venue.
     */
    fun findByVenueId(venueId: UUID): List<VenuePromoCode>

    // ============================================================================================
    // REGION: Commerce & Validation (Shopping Cart)
    // Use these methods when a customer is applying a code during checkout.
    // ============================================================================================

    /**
     * Retrieves a promo code entity by its exact string code.
     */
    fun findByVenueIdAndCode(venueId: UUID, code: String): Optional<VenuePromoCode>

    /**
     * Checks if a code string is already taken within a venue.
     */
    fun existsByVenueIdAndCode(venueId: UUID, code: String): Boolean

    /**
     * Finds a strictly **valid** promo code for immediate application (Read-Only).
     * Encapsulates logic: Active + Not Expired + Usage Limit Not Reached.
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
    fun findValidPromoCodeByVenueIdAndCode(
        venueId: UUID,
        code: String,
        currentTime: Instant
    ): Optional<VenuePromoCode>

    /**
     * Finds ALL valid promo codes for a venue.
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
    fun findValidPromoCodesByVenueId(venueId: UUID, currentTime: Instant): List<VenuePromoCode>

    // ============================================================================================
    // REGION: Usage Tracking (Atomic Operations)
    // Use these methods to safely track usage counts during checkout.
    // ============================================================================================

    /**
     * ATOMIC RESERVATION:
     * Increments the counter ONLY if the limit is not reached.
     * Returns 1 if successful (Reservation secured).
     * Returns 0 if limit reached (Reservation failed).
     */
    @Modifying
    @Query(
        """
        UPDATE VenuePromoCode p
        SET p.currentUsageCount = p.currentUsageCount + 1
        WHERE p.venue.id = :venueId 
        AND p.code = :code
        AND p.isActive = true
        AND (p.expiresAt IS NULL OR p.expiresAt > :now)
        AND (p.maxUsageCount IS NULL OR p.currentUsageCount < p.maxUsageCount)
    """
    )
    fun incrementUsageIfAllowed(venueId: UUID, code: String, now: Instant): Int

    /**
     * ATOMIC RELEASE:
     * Decrements the counter. Used when a booking is Cancelled or Expired.
     * Includes a safety check (currentUsageCount > 0) to prevent negative numbers.
     */
    @Modifying
    @Query(
        """
        UPDATE VenuePromoCode p
        SET p.currentUsageCount = p.currentUsageCount - 1
        WHERE p.venue.id = :venueId 
        AND p.code = :code
        AND p.currentUsageCount > 0
    """
    )
    fun decrementUsage(venueId: UUID, code: String)

    // ============================================================================================
    // REGION: Maintenance & Analytics
    // Use these methods for background jobs or reporting.
    // ============================================================================================

    /**
     * Finds all promo codes that have physically expired based on time.
     */
    @Query("SELECT p FROM VenuePromoCode p WHERE p.expiresAt IS NOT NULL AND p.expiresAt <= :currentTime")
    fun findExpiredPromoCodes(currentTime: Instant): List<VenuePromoCode>

    fun countByVenueIdAndIsActiveTrue(venueId: UUID): Long

    fun deleteByVenueId(venueId: UUID)
}