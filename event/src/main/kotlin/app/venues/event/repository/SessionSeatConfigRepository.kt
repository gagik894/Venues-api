package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionSeatConfig
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
     * Find config by session and seat identifier
     */
    @Query(
        """
        SELECT sc FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
        AND sc.seat.seatIdentifier = :seatIdentifier
    """
    )
    fun findBySessionIdAndSeatIdentifier(sessionId: Long, seatIdentifier: String): SessionSeatConfig?

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

    /**
     * Get availability statistics for session (optimized - count only).
     * Returns [totalSeats, availableSeats, reservedSeats]
     */
    @Query(
        """
        SELECT 
            COUNT(sc.id),
            SUM(CASE WHEN sc.status = 'AVAILABLE' THEN 1 ELSE 0 END),
            SUM(CASE WHEN sc.status = 'RESERVED' THEN 1 ELSE 0 END)
        FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
    """
    )
    fun getAvailabilityStatsRaw(sessionId: Long): Array<Long>

    /**
     * Get available seat identifiers (for small venues only).
     */
    @Query(
        """
        SELECT sc.seat.seatIdentifier FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
        AND sc.status = 'AVAILABLE'
        ORDER BY sc.seat.seatIdentifier
    """
    )
    fun findAvailableSeatIdentifiers(sessionId: Long): List<String>
}

