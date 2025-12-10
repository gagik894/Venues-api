package app.venues.event.repository

import app.venues.event.domain.Event
import app.venues.event.domain.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for Event entity operations.
 */
@Repository
interface EventRepository : JpaRepository<Event, UUID> {

    /**
     * Find events by venue ID
     */
    fun findByVenueId(venueId: UUID, pageable: Pageable): Page<Event>

    /**
     * Check if any event references the seating chart.
     */
    fun existsBySeatingChartId(seatingChartId: UUID): Boolean

    /**
     * Find events by venue ID and status
     */
    fun findByVenueIdAndStatus(venueId: UUID, status: EventStatus, pageable: Pageable): Page<Event>

    /**
     * Find events by venue ID and status, ordered by first session start (for API Port)
     */
    fun findByVenueIdAndStatusOrderByFirstSessionStartAsc(venueId: UUID, status: EventStatus): List<Event>

    /**
     * Find events by venue ID and multiple statuses
     */
    fun findByVenueIdAndStatusIn(venueId: UUID, statuses: Collection<EventStatus>, pageable: Pageable): Page<Event>

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
     * Find event by ID with all details loaded.
     */
    @EntityGraph(attributePaths = ["secondaryImgUrls", "tags", "translations", "category", "priceTemplates", "sessions"])
    override fun findById(id: UUID): Optional<Event>

    /**
     * Count events by venue ID
     */
    fun countByVenueId(venueId: UUID): Long

    /**
     * Count events by venue ID and status
     */
    fun countByVenueIdAndStatus(venueId: UUID, status: EventStatus): Long

    /**
     * Archive past events.
     */
    @Modifying
    @Query("UPDATE Event e SET e.status = :newStatus WHERE e.status = :oldStatus AND e.lastSessionEnd < :cutoff")
    fun archivePastEvents(oldStatus: EventStatus, newStatus: EventStatus, cutoff: Instant): Int
}

