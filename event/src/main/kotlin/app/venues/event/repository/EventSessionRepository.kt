package app.venues.event.repository

import app.venues.event.domain.EventSession
import app.venues.event.domain.SessionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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
    @Query("SELECT s FROM EventSession s JOIN FETCH s.event WHERE s.event.id = :eventId")
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
    fun findUpcomingSessions(eventId: UUID, status: SessionStatus, now: Instant): List<EventSession>

    /**
     * Find bookable sessions (upcoming with available tickets)
     */
    @Query(
        """
        SELECT s FROM EventSession s
        JOIN FETCH s.event
        WHERE s.event.id = :eventId
        AND s.status = 'ON_SALE'
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

    /**
     * Update status for expired sessions.
     */
    @Modifying
    @Query("UPDATE EventSession s SET s.status = :newStatus WHERE s.status = :oldStatus AND s.startTime < :now")
    fun updateStatusForExpiredSessions(oldStatus: SessionStatus, newStatus: SessionStatus, now: Instant): Int

    /**
     * Atomically increment tickets sold count.
     * Returns 1 if successful (enough capacity), 0 otherwise.
     */
    @Modifying
    @Query(
        """
        UPDATE EventSession s 
        SET s.ticketsSold = s.ticketsSold + :quantity 
        WHERE s.id = :sessionId 
        AND (s.ticketsCount IS NULL OR (s.ticketsSold + :quantity) <= s.ticketsCount)
    """
    )
    fun incrementTicketsSold(sessionId: UUID, quantity: Int): Int
}

