package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionSeatConfig
import app.venues.event.dto.AvailabilityStatsDto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

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
    fun findBySessionIdAndSeatId(sessionId: UUID, seatId: Long): SessionSeatConfig?

    /**
     * Find all available seat configs for session
     */
    fun findBySessionIdAndStatus(sessionId: UUID, status: ConfigStatus): List<SessionSeatConfig>

    /**
     * Find all seat configs for session
     */
    fun findBySessionId(sessionId: UUID): List<SessionSeatConfig>

    /**
     * Find all seat configs for a list of seat IDs
     */
    @Query(
        """
        SELECT sc FROM SessionSeatConfig sc
        LEFT JOIN FETCH sc.priceTemplate
        WHERE sc.session.id = :sessionId
        AND sc.seatId IN :seatIds
    """
    )
    fun findBySessionIdAndSeatIdIn(sessionId: UUID, seatIds: List<Long>): List<SessionSeatConfig>

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
    fun findAvailableSeatIdsBySession(sessionId: UUID): List<Long>

    /**
     * Count seats marked as SOLD for a session.
     * Used for ticket counter reconciliation.
     */
    @Query(
        """
        SELECT COUNT(sc)
        FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
        AND sc.status = app.venues.event.domain.ConfigStatus.SOLD
    """
    )
    fun countSoldSeats(sessionId: UUID): Long

    /**
     * Count seats for a session by status (supports sparse matrix).
     */
    @Query(
        """
        SELECT COUNT(sc)
        FROM SessionSeatConfig sc
        WHERE sc.session.id = :sessionId
        AND sc.status IN :statuses
    """
    )
    fun countBySessionIdAndStatusIn(sessionId: UUID, statuses: Collection<ConfigStatus>): Long

    /**
     * Atomically close multiple seats (set to CLOSED status).
     * Does not affect SOLD seats.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionSeatConfig sc
        SET sc.status = app.venues.event.domain.ConfigStatus.CLOSED
        WHERE sc.session.id = :sessionId
        AND sc.seatId IN :seatIds
        AND sc.status != app.venues.event.domain.ConfigStatus.SOLD
        """
    )
    fun closeSeats(sessionId: UUID, seatIds: List<Long>): Int

    /**
     * Atomically reopen previously closed seats (CLOSED -> AVAILABLE).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionSeatConfig sc
        SET sc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
        WHERE sc.session.id = :sessionId
        AND sc.seatId IN :seatIds
        AND sc.status = app.venues.event.domain.ConfigStatus.CLOSED
        """
    )
    fun reopenClosedSeats(sessionId: UUID, seatIds: List<Long>): Int

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
    fun getSeatPriceIfAvailable(sessionId: UUID, seatId: Long): java.math.BigDecimal?

    /**
     * Atomically reserve a seat if it's available AND return the price.
     * This prevents race conditions by doing price fetch + reservation in one operation.
     *
     * Uses a custom query with RETURNING clause (PostgreSQL).
     *
     * @param sessionId Event session ID
     * @param seatId Seat ID to reserve
     * @return Price if reservation successful, null if seat unavailable/not priced
     */
    @Query(
        nativeQuery = true,
        value = """
        WITH updated AS (
            UPDATE session_seat_configs
            SET status = 'RESERVED'
            WHERE session_id = :sessionId
            AND seat_id = :seatId
            AND status = 'AVAILABLE'
            RETURNING price_template_id
        )
        SELECT pt.price
        FROM updated u
        JOIN event_price_templates pt ON pt.id = u.price_template_id
    """
    )
    fun reserveSeatAndGetPrice(sessionId: UUID, seatId: Long): java.math.BigDecimal?

    /**
     * Atomically reserves a seat if it is available.
     * Returns 1 if successful, 0 if seat was not found or not available.
     */
    @Modifying
    @Query("UPDATE SessionSeatConfig s SET s.status = app.venues.event.domain.ConfigStatus.RESERVED WHERE s.session.id = :sessionId AND s.seatId = :seatId AND s.status = app.venues.event.domain.ConfigStatus.AVAILABLE")
    fun reserveSeatAtomic(sessionId: UUID, seatId: Long): Int

    /**
     * Atomically block multiple seats (set to BLOCKED status).
     * Used when reserving a table to block all its seats in one operation.
     *
     * @param sessionId Event session ID
     * @param seatIds List of seat IDs to block
     * @return Number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionSeatConfig sc
        SET sc.status = app.venues.event.domain.ConfigStatus.BLOCKED
        WHERE sc.session.id = :sessionId
        AND sc.seatId IN :seatIds
        AND sc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
    """
    )
    fun blockSeats(sessionId: UUID, seatIds: List<Long>): Int

    /**
     * Atomically unblock multiple seats (set to AVAILABLE status).
     * Used when releasing a table reservation.
     *
     * @param sessionId Event session ID
     * @param seatIds List of seat IDs to unblock
     * @return Number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionSeatConfig sc
        SET sc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
        WHERE sc.session.id = :sessionId
        AND sc.seatId IN :seatIds
        AND sc.status = app.venues.event.domain.ConfigStatus.BLOCKED
    """
    )
    fun unblockSeats(sessionId: UUID, seatIds: List<Long>): Int

    /**
     * BATCH operation: Release multiple seats atomically (RESERVED -> AVAILABLE).
     * Optimized for cart cleanup and bulk operations.
     *
     * Performance: Single UPDATE instead of N queries.
     *
     * @param sessionId Event session ID
     * @param seatIds List of seat IDs to release
     * @return Number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionSeatConfig sc
        SET sc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
        WHERE sc.session.id = :sessionId
        AND sc.seatId IN :seatIds
        AND sc.status = app.venues.event.domain.ConfigStatus.RESERVED
    """
    )
    fun releaseSeats(sessionId: UUID, seatIds: List<Long>): Int

    /**
     * BATCH operation: Update price template for multiple seats.
     * Skips seats that are SOLD.
     *
     * @param sessionId Event session ID
     * @param seatIds List of seat IDs to update
     * @param template The new price template (can be null)
     * @return Number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionSeatConfig sc
        SET sc.priceTemplate = :template
        WHERE sc.session.id = :sessionId
        AND sc.seatId IN :seatIds
        AND sc.status NOT IN (app.venues.event.domain.ConfigStatus.SOLD, app.venues.event.domain.ConfigStatus.RESERVED)
    """
    )
    fun batchUpdatePriceTemplate(
        sessionId: UUID,
        seatIds: List<Long>,
        template: app.venues.event.domain.EventPriceTemplate?
    ): Int

    /**
     * BATCH operation: Sell multiple seats atomically (RESERVED -> SOLD).
     *
     * @param sessionId Event session ID
     * @param seatIds List of seat IDs to sell
     * @return Number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionSeatConfig sc
        SET sc.status = app.venues.event.domain.ConfigStatus.SOLD
        WHERE sc.session.id = :sessionId
        AND sc.seatId IN :seatIds
        AND sc.status = app.venues.event.domain.ConfigStatus.RESERVED
    """
    )
    fun sellSeats(sessionId: UUID, seatIds: List<Long>): Int

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
    fun getAvailabilityStatsRaw(sessionId: UUID): AvailabilityStatsDto?

    /**
     * Check if any configs exist for the session.
     */
    fun existsBySessionId(sessionId: UUID): Boolean
}
