package app.venues.event.repository

import app.venues.event.domain.EventSession
import app.venues.event.domain.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for EventSession entity operations.
 */
@Repository
interface EventSessionRepository : JpaRepository<EventSession, UUID> {

    /**
     * Find sessions by event ID
     */
    fun findByEventId(eventId: UUID, pageable: Pageable): Page<EventSession>

    /**
     * Find sessions by event ID ordered by start time
     */
    fun findByEventIdOrderByStartTimeAsc(eventId: UUID): List<EventSession>

    /**
     * Find upcoming sessions for an event
     */
    @Query(
        """
        SELECT s FROM EventSession s
        WHERE s.event.id = :eventId
        AND s.status = :status
        AND s.startTime > :now
        ORDER BY s.startTime ASC
    """
    )
    fun findUpcomingSessions(eventId: UUID, status: EventStatus, now: Instant): List<EventSession>

    /**
     * Find bookable sessions (upcoming with available tickets)
     */
    @Query(
        """
        SELECT s FROM EventSession s
        WHERE s.event.id = :eventId
        AND s.status = 'UPCOMING'
        AND s.startTime > :now
        AND (s.ticketsCount IS NULL OR s.ticketsSold < s.ticketsCount)
        ORDER BY s.startTime ASC
    """
    )
    fun findBookableSessions(eventId: UUID, now: Instant): List<EventSession>

    /**
     * Count sessions by event ID
     */
    fun countByEventId(eventId: UUID): Long
}

