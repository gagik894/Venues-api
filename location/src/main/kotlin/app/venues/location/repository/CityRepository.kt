package app.venues.location.repository

import app.venues.location.domain.City
import app.venues.location.domain.Region
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository for City reference data operations.
 *
 * Provides optimized queries for city lookup, filtering, and search.
 * Cities are linked to regions and support multilingual search.
 *
 * Query Optimization:
 * - Indexed by slug for fast API lookups
 * - Indexed by region for filtered listings
 * - Active status filtering for UI display
 * - JSONB search for multilingual name queries
 */
@Repository
interface CityRepository : JpaRepository<City, Long> {

    /**
     * Find city by slug (URL-friendly identifier).
     *
     * Primary lookup method for API endpoints.
     * Example: GET /api/v1/cities/gyumri
     *
     * @param slug City slug
     * @return City if found, null otherwise
     */
    fun findBySlug(slug: String): City?

    /**
     * Check if city slug exists (for validation).
     *
     * @param slug City slug
     * @return true if slug exists, false otherwise
     */
    fun existsBySlug(slug: String): Boolean

    /**
     * Get all active cities ordered by display order.
     *
     * Use for UI dropdowns and selection lists.
     *
     * @return List of active cities in display order
     */
    @Query(
        """
        SELECT c FROM City c 
        WHERE c.isActive = true 
        ORDER BY c.displayOrder NULLS LAST, c.slug ASC
    """
    )
    fun findAllActive(): List<City>

    /**
     * Get all active cities in a specific region.
     *
     * Use for region-filtered city selection.
     *
     * @param region Parent region
     * @return List of active cities in the region
     */
    @Query(
        """
        SELECT c FROM City c 
        WHERE c.region = :region AND c.isActive = true 
        ORDER BY c.displayOrder NULLS LAST, c.slug ASC
    """
    )
    fun findAllActiveByRegion(@Param("region") region: Region): List<City>

    /**
     * Search cities by name (multilingual, case-insensitive).
     *
     * Searches across all language names in the JSONB field.
     * Note: This query is less performant than slug lookup; use sparingly.
     *
     * @param searchTerm Search string (partial match)
     * @param pageable Pagination parameters
     * @return Page of matching cities
     */
    @Query(
        value = """
        SELECT * FROM ref_cities c 
        WHERE c.is_active = true 
        AND (
            c.slug ILIKE CONCAT('%', :searchTerm, '%') 
            OR c.names ->> 'hy' ILIKE CONCAT('%', :searchTerm, '%')
            OR c.names ->> 'en' ILIKE CONCAT('%', :searchTerm, '%')
            OR c.names ->> 'ru' ILIKE CONCAT('%', :searchTerm, '%')
        ) 
        ORDER BY c.display_order ASC NULLS LAST, c.slug ASC
        """,
        countQuery = """
        SELECT count(*) FROM ref_cities c 
        WHERE c.is_active = true 
        AND (
            c.slug ILIKE CONCAT('%', :searchTerm, '%') 
            OR c.names ->> 'hy' ILIKE CONCAT('%', :searchTerm, '%')
            OR c.names ->> 'en' ILIKE CONCAT('%', :searchTerm, '%')
            OR c.names ->> 'ru' ILIKE CONCAT('%', :searchTerm, '%')
        )
        """,
        nativeQuery = true
    )
    fun searchByName(@Param("searchTerm") searchTerm: String, pageable: Pageable): Page<City>

    /**
     * Find city by official cadastre ID.
     *
     * Use for integration with government cadastre systems.
     *
     * @param officialId Official cadastre/government ID
     * @return City if found, null otherwise
     */
    fun findByOfficialId(officialId: String): City?

    /**
     * Get all cities (including inactive) for admin purposes.
     *
     * @param pageable Pagination parameters
     * @return Page of all cities
     */
    @Query(
        """
        SELECT c FROM City c 
        LEFT JOIN FETCH c.region 
        ORDER BY c.displayOrder NULLS LAST, c.slug ASC
    """
    )
    fun findAllWithRegion(pageable: Pageable): Page<City>

    /**
     * Count cities by region.
     *
     * @param regionId Region ID
     * @return Number of cities in the region
     */
    fun countByRegionIdAndIsActive(regionId: Long, isActive: Boolean = true): Long
}

