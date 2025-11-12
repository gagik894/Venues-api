package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionTableConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal

/**
 * Repository for SessionTableConfig operations.
 *
 * Provides atomic operations for table reservation and status management.
 */
@Repository
interface SessionTableConfigRepository : JpaRepository<SessionTableConfig, Long> {

    /**
     * Find table config by session and table ID
     */
    fun findBySessionIdAndTableId(sessionId: Long, tableId: Long): SessionTableConfig?

    /**
     * Find all table configs for a session
     */
    fun findBySessionId(sessionId: Long): List<SessionTableConfig>

    /**
     * Find all available tables for a session
     */
    fun findBySessionIdAndStatus(sessionId: Long, status: ConfigStatus): List<SessionTableConfig>

    /**
     * Get table price if available and priced
     * Returns null if table is not available or not priced
     */
    @Query(
        """
        SELECT pt.price 
        FROM SessionTableConfig stc
        JOIN stc.priceTemplate pt
        WHERE stc.session.id = :sessionId
        AND stc.tableId = :tableId
        AND stc.status = 'AVAILABLE'
        AND pt.price IS NOT NULL
    """
    )
    fun getTablePriceIfAvailable(sessionId: Long, tableId: Long): BigDecimal?

    /**
     * Atomically reserve a table if available.
     * Updates status from AVAILABLE to RESERVED.
     * Returns number of rows affected (0 if already reserved, 1 if successful).
     */
    @Modifying
    @Query(
        """
        UPDATE SessionTableConfig stc
        SET stc.status = 'RESERVED'
        WHERE stc.session.id = :sessionId
        AND stc.tableId = :tableId
        AND stc.status = 'AVAILABLE'
    """
    )
    fun reserveTableIfAvailable(sessionId: Long, tableId: Long): Int

    /**
     * Block table (when any of its seats are reserved/sold)
     */
    @Modifying
    @Query(
        """
        UPDATE SessionTableConfig stc
        SET stc.status = 'BLOCKED'
        WHERE stc.session.id = :sessionId
        AND stc.tableId = :tableId
        AND stc.status = 'AVAILABLE'
    """
    )
    fun blockTable(sessionId: Long, tableId: Long): Int

    /**
     * Unblock table (when all its seats become available again)
     */
    @Modifying
    @Query(
        """
        UPDATE SessionTableConfig stc
        SET stc.status = 'AVAILABLE'
        WHERE stc.session.id = :sessionId
        AND stc.tableId = :tableId
        AND stc.status = 'BLOCKED'
    """
    )
    fun unblockTable(sessionId: Long, tableId: Long): Int

    /**
     * Find all tables that contain a specific seat
     * Used to block/unblock tables when seat status changes
     */
    @Query(
        """
        SELECT stc 
        FROM SessionTableConfig stc
        WHERE stc.session.id = :sessionId
        AND stc.tableId IN (
            SELECT s.level.id
            FROM Seat s
            WHERE s.id = :seatId
            AND s.level.isTable = true
        )
    """
    )
    fun findTablesBySeatId(sessionId: Long, seatId: Long): List<SessionTableConfig>
}

