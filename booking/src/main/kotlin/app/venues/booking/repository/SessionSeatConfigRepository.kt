package app.venues.booking.repository

import app.venues.booking.domain.ConfigStatus
import app.venues.booking.domain.SessionSeatConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for SessionSeatConfig entity operations.
 */
@Repository
interface SessionSeatConfigRepository : JpaRepository<SessionSeatConfig, Long> {

    /**
     * Find config by session and seat
     */
    fun findBySessionIdAndSeatId(sessionId: Long, seatId: Long): SessionSeatConfig?

    /**
     * Find all available seat configs for session
     */
    fun findBySessionIdAndStatus(sessionId: Long, status: ConfigStatus): List<SessionSeatConfig>

    /**
     * Find all seat configs for session
     */
    fun findBySessionId(sessionId: Long): List<SessionSeatConfig>

    /**
     * Get seat IDs that are available for session
     */
    @Query(
        """
        SELECT sc.seat.id FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
        AND sc.status = 'AVAILABLE'
    """
    )
    fun findAvailableSeatIdsBySession(sessionId: Long): List<Long>
}

