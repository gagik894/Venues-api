package app.venues.event.repository

import app.venues.event.domain.Event
import app.venues.event.domain.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for Event entity operations.
 */
@Repository
interface EventRepository : JpaRepository<Event, Long> {

    /**
     * Find events by venue ID
     */
    fun findByVenueId(venueId: Long, pageable: Pageable): Page<Event>

    /**
     * Find events by venue ID and status
     */
    fun findByVenueIdAndStatus(venueId: Long, status: EventStatus, pageable: Pageable): Page<Event>

    /**
     * Find events by status
     */
    fun findByStatus(status: EventStatus, pageable: Pageable): Page<Event>

    /**
     * Find events by category ID
     */
    fun findByCategoryId(categoryId: Long, pageable: Pageable): Page<Event>

    /**
     * Find events by category ID and status
     */
    fun findByCategoryIdAndStatus(categoryId: Long, status: EventStatus, pageable: Pageable): Page<Event>

    /**
     * Search events by title (case-insensitive)
     */
    @Query(
        """
        SELECT e FROM Event e 
        WHERE LOWER(e.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        AND e.status = :status
    """
    )
    fun searchByTitle(searchTerm: String, status: EventStatus, pageable: Pageable): Page<Event>

    /**
     * Find events by tag
     */
    @Query(
        """
        SELECT e FROM Event e 
        JOIN e.tags t
        WHERE t = :tag
        AND e.status = :status
    """
    )
    fun findByTag(tag: String, status: EventStatus, pageable: Pageable): Page<Event>

    /**
     * Count events by venue ID
     */
    fun countByVenueId(venueId: Long): Long

    /**
     * Count events by venue ID and status
     */
    fun countByVenueIdAndStatus(venueId: Long, status: EventStatus): Long
}

