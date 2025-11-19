package app.venues.venue.repository

import app.venues.location.domain.City
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
 * Repository for Venue entity.
 *
 * Provides data access methods for venue management with location-based filtering.
 */
@Repository
interface VenueRepository : JpaRepository<Venue, UUID> {
    /**
     * Find active venues (for public listing)
     */
    fun findByStatus(status: VenueStatus, pageable: Pageable): Page<Venue>

    /**
     * Find venues by city (entity relationship).
     *
     * @param city City entity
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues in the specified city
     */
    fun findByCityAndStatus(city: City, status: VenueStatus, pageable: Pageable): Page<Venue>

    /**
     * Find venues by city ID.
     *
     * @param cityId City ID
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues in the specified city
     */
    @Query("SELECT v FROM Venue v WHERE v.city.id = :cityId AND v.status = :status")
    fun findByCityIdAndStatus(
        @Param("cityId") cityId: Long,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Find venues by city slug (URL-friendly lookup).
     *
     * @param citySlug City slug (e.g., "gyumri", "yerevan")
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues in the city matching the slug
     */
    @Query("SELECT v FROM Venue v WHERE v.city.slug = :citySlug AND v.status = :status")
    fun findByCitySlugAndStatus(
        @Param("citySlug") citySlug: String,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Find venues by region ID.
     *
     * @param regionId Region ID
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of venues in all cities within the region
     */
    @Query("SELECT v FROM Venue v WHERE v.city.region.id = :regionId AND v.status = :status")
    fun findByRegionIdAndStatus(
        @Param("regionId") regionId: Long,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Find venues by region code (ISO code lookup).
     *
     * @param regionCode ISO region code (e.g., "AM-ER", "AM-SH")
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
     * Find venues by category
     */
    fun findByCategoryAndStatus(category: String, status: VenueStatus, pageable: Pageable): Page<Venue>

    /**
     * Search venues by name (case-insensitive)
     */
    @Query("SELECT v FROM Venue v WHERE LOWER(v.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND v.status = :status")
    fun searchByName(
        @Param("searchTerm") searchTerm: String,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Search venues by name in a specific city.
     *
     * @param searchTerm Search term (partial name match)
     * @param cityId City ID filter
     * @param status Venue status filter
     * @param pageable Pagination parameters
     * @return Page of matching venues
     */
    @Query(
        """
        SELECT v FROM Venue v 
        WHERE LOWER(v.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) 
        AND v.city.id = :cityId 
        AND v.status = :status
    """
    )
    fun searchByNameInCity(
        @Param("searchTerm") searchTerm: String,
        @Param("cityId") cityId: Long,
        @Param("status") status: VenueStatus,
        pageable: Pageable
    ): Page<Venue>

    /**
     * Find verified venues
     */
    fun findByVerifiedAndStatus(verified: Boolean, status: VenueStatus, pageable: Pageable): Page<Venue>

    /**
     * Find venues near a location (simplified - using bounding box)
     * For production, consider using PostGIS spatial queries
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
     * Count venues by status
     */
    fun countByStatus(status: VenueStatus): Long

    /**
     * Count venues by city.
     *
     * @param cityId City ID
     * @param status Venue status filter (optional)
     * @return Number of venues in the city
     */
    fun countByCityIdAndStatus(cityId: Long, status: VenueStatus): Long

    /**
     * Count venues by region.
     *
     * @param regionId Region ID
     * @param status Venue status filter (optional)
     * @return Number of venues in the region
     */
    @Query("SELECT COUNT(v) FROM Venue v WHERE v.city.region.id = :regionId AND v.status = :status")
    fun countByRegionIdAndStatus(
        @Param("regionId") regionId: Long,
        @Param("status") status: VenueStatus
    ): Long

    /**
     * Find all venues with status NOT DELETED
     */
    @Query("SELECT v FROM Venue v WHERE v.status != 'DELETED'")
    fun findAllActive(pageable: Pageable): Page<Venue>
}

