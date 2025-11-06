package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionSeatConfig
import app.venues.event.dto.AvailabilityStatsDto
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
        SELECT sc.seatId FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
        AND sc.status = 'AVAILABLE'
    """
    )
    fun findAvailableSeatIdsBySession(sessionId: Long): List<Long>

    /**
     * Get availability statistics for session (optimized - count only).
     * Returns aggregated seat counts: total, available, and reserved.
     */
    @Query(
        """
        SELECT NEW app.venues.event.dto.AvailabilityStatsDto(
            COUNT(sc.id),
            SUM(CASE WHEN sc.status = 'AVAILABLE' THEN 1 ELSE 0 END),
            SUM(CASE WHEN sc.status = 'RESERVED' THEN 1 ELSE 0 END)
        )        
        FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
    """
    )
    fun getAvailabilityStatsRaw(sessionId: Long): AvailabilityStatsDto?
}