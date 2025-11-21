package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionTableConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

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
    fun findBySessionIdAndTableId(sessionId: UUID, tableId: Long): SessionTableConfig?

    /**
     * Find all table configs for a session
     */
    fun findBySessionId(sessionId: UUID): List<SessionTableConfig>

    /**
     * Find all available tables for a session
     */
    fun findBySessionIdAndStatus(sessionId: UUID, status: ConfigStatus): List<SessionTableConfig>

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
    fun getTablePriceIfAvailable(sessionId: UUID, tableId: Long): BigDecimal?

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
    fun reserveTableIfAvailable(sessionId: UUID, tableId: Long): Int

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
    fun blockTable(sessionId: UUID, tableId: Long): Int

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
    fun unblockTable(sessionId: UUID, tableId: Long): Int


    /**
     * Atomically unblocks a table ONLY IF all its constituent seats are available.
     * This prevents a TOCTOU race condition.
     *
     * @param sessionId The session ID
     * @param tableId The table to unblock
     * @param tableSeatIds The complete list of seat IDs that belong to this table
     * @return Number of rows affected (0 if seats were not all available, 1 if unblocked)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionTableConfig stc
        SET stc.status = 'AVAILABLE'
        WHERE stc.session.id = :sessionId
        AND stc.tableId = :tableId
        AND stc.status = 'BLOCKED'
        AND NOT EXISTS (
            SELECT 1
            FROM SessionSeatConfig ssc
            WHERE ssc.session.id = :sessionId
            AND ssc.seatId IN :tableSeatIds
            AND ssc.status != 'AVAILABLE'
        )
    """
    )
    fun unblockTableIfAllSeatsAreAvailable(
        sessionId: UUID,
        tableId: Long,
        tableSeatIds: List<Long>
    ): Int

    /**
     * BATCH operation: Release multiple tables atomically (RESERVED -> AVAILABLE).
     * Optimized for cart cleanup operations.
     *
     * Performance: Single UPDATE instead of N queries.
     *
     * @param sessionId Session ID
     * @param tableIds List of table IDs to release
     * @return Number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionTableConfig stc
        SET stc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
        WHERE stc.session.id = :sessionId
        AND stc.tableId IN :tableIds
        AND stc.status = app.venues.event.domain.ConfigStatus.RESERVED
    """
    )
    fun releaseTables(sessionId: UUID, tableIds: List<Long>): Int

    /**
     * BATCH operation: Update price template for multiple tables.
     * Skips tables that are SOLD.
     *
     * @param sessionId Event session ID
     * @param tableIds List of table IDs to update
     * @param template The new price template (can be null)
     * @return Number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionTableConfig stc
        SET stc.priceTemplate = :template
        WHERE stc.session.id = :sessionId
        AND stc.tableId IN :tableIds
        AND stc.status != app.venues.event.domain.ConfigStatus.SOLD
    """
    )
    fun batchUpdatePriceTemplate(
        sessionId: UUID,
        tableIds: List<Long>,
        template: app.venues.event.domain.EventPriceTemplate?
    ): Int

    /**
     * BATCH operation: Sell multiple tables atomically (RESERVED -> SOLD).
     *
     * @param sessionId Event session ID
     * @param tableIds List of table IDs to sell
     * @return Number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionTableConfig stc
        SET stc.status = app.venues.event.domain.ConfigStatus.SOLD
        WHERE stc.session.id = :sessionId
        AND stc.tableId IN :tableIds
        AND stc.status = app.venues.event.domain.ConfigStatus.RESERVED
    """
    )
    fun sellTables(sessionId: UUID, tableIds: List<Long>): Int

    /**
     * Check if any configs exist for the session.
     */
    fun existsBySessionId(sessionId: UUID): Boolean
}
