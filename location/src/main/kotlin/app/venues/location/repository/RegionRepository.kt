package app.venues.location.repository

import app.venues.location.domain.Region
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for Region reference data operations.
 *
 * Provides optimized queries for region lookup and listing.
 * Regions are reference data (rarely change), making them ideal
 * candidates for caching at the service layer.
 *
 * Query Optimization:
 * - Indexed by code for fast government system lookups
 * - Active status filtering for UI display
 * - Ordered results for consistent presentation
 */
@Repository
interface RegionRepository : JpaRepository<Region, Long> {

    /**
     * Find region by ISO/government code.
     *
     * Example codes: "AM-ER", "AM-SH", "AM-LO"
     *
     * @param code Region code
     * @return Region if found, null otherwise
     */
    fun findByCode(code: String): Region?

    /**
     * Check if region code exists (for validation).
     *
     * @param code Region code
     * @return true if code exists, false otherwise
     */
    fun existsByCode(code: String): Boolean

    /**
     * Get all active regions ordered by display order, then name.
     *
     * Use for UI dropdowns and selection lists.
     * Results are deterministic and cacheable.
     *
     * @return List of active regions in display order
     */
    @Query(
        """
        SELECT r FROM Region r 
        WHERE r.isActive = true 
        ORDER BY r.displayOrder NULLS LAST, r.id ASC
    """
    )
    fun findAllActive(): List<Region>

    /**
     * Get all regions (including inactive) for admin purposes.
     *
     * @return List of all regions
     */
    @Query(
        """
        SELECT r FROM Region r 
        ORDER BY r.displayOrder NULLS LAST, r.id ASC
    """
    )
    override fun findAll(): List<Region>
}




