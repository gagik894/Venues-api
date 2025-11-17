package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionLevelConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for SessionLevelConfig entity operations.
 */
@Repository
interface SessionLevelConfigRepository : JpaRepository<SessionLevelConfig, Long> {

    /**
     * Find config by session and level with eagerly loaded priceTemplate
     */
    @Query(
        """
        SELECT slc FROM SessionLevelConfig slc
        LEFT JOIN FETCH slc.priceTemplate
        WHERE slc.session.id = :sessionId
        AND slc.levelId = :levelId
    """
    )
    fun findBySessionIdAndLevelId(sessionId: UUID, levelId: Long): SessionLevelConfig?

    /**
     * Find all level configs for a list of level IDs
     */
    @Query(
        """
        SELECT slc FROM SessionLevelConfig slc
        LEFT JOIN FETCH slc.priceTemplate
        WHERE slc.session.id = :sessionId
        AND slc.levelId IN :levelIds
    """
    )
    fun findBySessionIdAndLevelIdIn(sessionId: UUID, levelIds: List<Long>): List<SessionLevelConfig>

    /**
     * Find all available level configs for session
     */
    fun findBySessionIdAndStatus(sessionId: UUID, status: ConfigStatus): List<SessionLevelConfig>

    /**
     * Find all level configs for session
     */
    fun findBySessionId(sessionId: UUID): List<SessionLevelConfig>

    /**
     * Atomically reserve GA level tickets if available capacity exists.
     * Returns the price that was active at reservation time.
     *
     * @param sessionId Event session ID
     * @param levelId Level ID to reserve from
     * @param quantity Number of tickets to reserve
     * @return Price from template if reservation successful, null if failed
     */
    @Query(
        """
        SELECT pt.price
        FROM SessionLevelConfig slc
        JOIN slc.priceTemplate pt
        WHERE slc.session.id = :sessionId
        AND slc.levelId = :levelId
        AND slc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
        AND (slc.capacity - slc.soldCount) >= :quantity
        AND slc.priceTemplate IS NOT NULL
    """
    )
    fun getGAPriceIfAvailable(sessionId: UUID, levelId: Long, quantity: Int): java.math.BigDecimal?

    /**
     * Atomically reserve GA level tickets if available capacity exists AND return price.
     * This prevents race conditions by doing price fetch + reservation in one operation.
     *
     * Uses native SQL with RETURNING clause (PostgreSQL).
     *
     * @param sessionId Event session ID
     * @param levelId Level ID to reserve from
     * @param quantity Number of tickets to reserve
     * @return Price if reservation successful, null if no rows affected
     */
    @Query(
        nativeQuery = true,
        value = """
        WITH updated AS (
            UPDATE session_level_configs
            SET sold_count = sold_count + :quantity
            WHERE session_id = :sessionId
            AND level_id = :levelId
            AND status = 'AVAILABLE'
            AND (capacity - sold_count) >= :quantity
            RETURNING price_template_id
        )
        SELECT pt.price
        FROM updated u
        JOIN event_price_templates pt ON pt.id = u.price_template_id
    """
    )
    fun reserveGAAndGetPrice(sessionId: UUID, levelId: Long, quantity: Int): java.math.BigDecimal?

    /**
     * Atomically adjust the sold count for a GA level.
     * This query will fail if the new total exceeds capacity or goes below zero.
     *
     * @param sessionId Event session ID
     * @param levelId Level ID to adjust
     * @param quantityDelta Positive number to add tickets, negative to release
     * @return Number of rows affected (0 if adjustment failed capacity check, 1 if successful)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionLevelConfig slc
        SET slc.soldCount = slc.soldCount + :quantityDelta
        WHERE slc.session.id = :sessionId
        AND slc.levelId = :levelId
        AND (slc.soldCount + :quantityDelta) >= 0
        AND (slc.soldCount + :quantityDelta) <= slc.capacity
    """
    )
    fun adjustGATickets(sessionId: UUID, levelId: Long, quantityDelta: Int): Int
}

