package app.venues.venue.repository

import app.venues.venue.domain.VenueCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for VenueCategory reference data.
 */
@Repository
interface VenueCategoryRepository : JpaRepository<VenueCategory, Long> {

    /**
     * Finds active category by code.
     *
     * @param code Category code (e.g., "OPERA", "MUSEUM")
     * @return VenueCategory if found and active
     */
    fun findByCodeAndIsActive(code: String, isActive: Boolean = true): VenueCategory?

    /**
     * Finds all active categories ordered by display order.
     *
     * @return List of active categories
     */
    @Query("SELECT c FROM VenueCategory c WHERE c.isActive = true ORDER BY c.displayOrder ASC")
    fun findAllActive(): List<VenueCategory>

    /**
     * Checks if category code exists.
     *
     * @param code Category code
     * @return true if exists
     */
    fun existsByCode(code: String): Boolean
}

