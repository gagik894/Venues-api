package app.venues.venue.repository

import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenueStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Venue entity operations.
 * Uses slug-based lookups for public APIs (never exposes UUIDs).
 */
@Repository
interface VenueRepository : JpaRepository<Venue, UUID> {

    /**
     * Finds venue by slug (primary public lookup).
     *
     * @param slug URL-friendly venue identifier
     * @return Venue if found
     */
    fun findBySlug(slug: String): Venue?

    /**
     * Checks if slug exists (for validation).
     *
     * @param slug Venue slug
     * @return true if exists
     */
    fun existsBySlug(slug: String): Boolean

    /**
     * Finds venues by status with pagination.
     *
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues
     */
    fun findByStatus(status: VenueStatus, pageable: Pageable): Page<Venue>

    /**
     * Finds venues by city slug and status.
     *
     * @param citySlug City slug filter
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues in the specified city
     */
    @Query("SELECT v FROM Venue v WHERE v.city.slug = :citySlug AND v.status = :status")
    fun findByCitySlugAndStatus(
        @Param("citySlug") citySlug: String,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Finds venues by region code and status.
     *
     * @param regionCode ISO region code (e.g., "AM-ER")
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues in the region
     */
    @Query("SELECT v FROM Venue v WHERE v.city.region.code = :regionCode AND v.status = :status")
    fun findByRegionCodeAndStatus(
        @Param("regionCode") regionCode: String,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Finds venues by category code and status.
     *
     * @param categoryCode Category code (e.g., "OPERA")
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues in the category
     */
    @Query("SELECT v FROM Venue v WHERE v.category.code = :categoryCode AND v.status = :status")
    fun findByCategoryCodeAndStatus(
        @Param("categoryCode") categoryCode: String,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Searches venues by name (case-insensitive).
     *
     * @param searchTerm Search string
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of matching venues
     */
    @Query(
        """
        SELECT v FROM Venue v 
        WHERE LOWER(v.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) 
        AND v.status = :status
    """
    )
    fun searchByName(
        @Param("searchTerm") searchTerm: String,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Finds venues within geographic bounds.
     * Simple bounding box approach (for PostGIS, use spatial queries).
     *
     * @param minLat Minimum latitude
     * @param maxLat Maximum latitude
     * @param minLon Minimum longitude
     * @param maxLon Maximum longitude
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues within bounds
     */
    @Query(
        """
        SELECT v FROM Venue v 
        WHERE v.latitude BETWEEN :minLat AND :maxLat 
        AND v.longitude BETWEEN :minLon AND :maxLon
        AND v.status = :status
    """
    )
    fun findNearLocation(
        @Param("minLat") minLat: Double,
        @Param("maxLat") maxLat: Double,
        @Param("minLon") minLon: Double,
        @Param("maxLon") maxLon: Double,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Counts venues by status.
     *
     * @param status Venue status
     * @return Number of venues
     */
    fun countByStatus(status: VenueStatus): Long

    /**
     * Counts venues by city.
     *
     * @param citySlug City slug
     * @param status Venue status filter
     * @return Number of venues in city
     */
    @Query("SELECT COUNT(v) FROM Venue v WHERE v.city.slug = :citySlug AND v.status = :status")
    fun countByCitySlugAndStatus(
        @Param("citySlug") citySlug: String,
        @Param("status") status: VenueStatus
    ): Long

    /**
     * Finds all non-deleted venues (admin view).
     *
     * @param pageable Pagination parameters
     * @return Page of venues
     */
    @Query("SELECT v FROM Venue v WHERE v.status != 'DELETED'")
    fun findAllNonDeleted(pageable: Pageable): Page<Venue>
}

