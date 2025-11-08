package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionSeatConfig
import app.venues.event.dto.AvailabilityStatsDto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for SessionSeatConfig entity operations.
 */
@Repository
interface SessionSeatConfigRepository : JpaRepository<SessionSeatConfig, Long> {

    /**
     * Find config by session and seat with eagerly loaded priceTemplate
     */
    @Query(
        """
        SELECT sc FROM SessionSeatConfig sc
        LEFT JOIN FETCH sc.priceTemplate
        WHERE sc.session.id = :sessionId
        AND sc.seatId = :seatId
    """
    )
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
        AND sc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
    """
    )
    fun findAvailableSeatIdsBySession(sessionId: Long): List<Long>

    /**
     * Get the price for a seat if it's available for reservation.
     * This should be called BEFORE reserveSeatIfAvailable to check price.
     *
     * @param sessionId Event session ID
     * @param seatId Seat ID
     * @return Price from template if seat is available, null if not available or not priced
     */
    @Query(
        """
        SELECT pt.price
        FROM SessionSeatConfig sc
        JOIN sc.priceTemplate pt
        WHERE sc.session.id = :sessionId
        AND sc.seatId = :seatId
        AND sc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
        AND sc.priceTemplate IS NOT NULL
    """
    )
    fun getSeatPriceIfAvailable(sessionId: Long, seatId: Long): java.math.BigDecimal?

    /**
     * Atomically reserve a seat if it's available.
     * @param sessionId Event session ID
     * @param seatId Seat ID to reserve
     * @return Number of rows updated (0 or 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionSeatConfig sc
        SET sc.status = app.venues.event.domain.ConfigStatus.RESERVED
        WHERE sc.session.id = :sessionId
        AND sc.seatId = :seatId
        AND sc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
    """
    )
    fun reserveSeatIfAvailable(sessionId: Long, seatId: Long): Int

    /**
     * Get availability statistics for session (optimized - count only).
     * Returns aggregated seat counts: total, available, and reserved.
     */
    @Query(
        """
        SELECT NEW app.venues.event.dto.AvailabilityStatsDto(
            COUNT(sc.id),
            SUM(CASE WHEN sc.status = app.venues.event.domain.ConfigStatus.AVAILABLE THEN 1 ELSE 0 END),
            SUM(CASE WHEN sc.status = app.venues.event.domain.ConfigStatus.RESERVED THEN 1 ELSE 0 END)
        )        
        FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
    """
    )
    fun getAvailabilityStatsRaw(sessionId: Long): AvailabilityStatsDto?
}