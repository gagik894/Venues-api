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
 * Repository for Venue entity.
 *
 * Provides data access methods for venue management.
 */
@Repository
interface VenueRepository : JpaRepository<Venue, UUID> {

    /**
     * Find venue by email (for authentication)
     */
    fun findByEmail(email: String): Optional<Venue>

    /**
     * Check if email already exists
     */
    fun existsByEmail(email: String): Boolean

    /**
     * Find active venues (for public listing)
     */
    fun findByStatus(status: VenueStatus, pageable: Pageable): Page<Venue>

    /**
     * Find venues by city
     */
    fun findByCityAndStatus(city: String, status: VenueStatus, pageable: Pageable): Page<Venue>

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
     * Find all venues with status NOT DELETED
     */
    @Query("SELECT v FROM Venue v WHERE v.status != 'DELETED'")
    fun findAllActive(pageable: Pageable): Page<Venue>
}

