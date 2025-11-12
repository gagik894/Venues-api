package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionLevelConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

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
    fun findBySessionIdAndLevelId(sessionId: Long, levelId: Long): SessionLevelConfig?


    /**
     * Find all available level configs for session
     */
    fun findBySessionIdAndStatus(sessionId: Long, status: ConfigStatus): List<SessionLevelConfig>

    /**
     * Find all level configs for session
     */
    fun findBySessionId(sessionId: Long): List<SessionLevelConfig>

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
    fun getGAPriceIfAvailable(sessionId: Long, levelId: Long, quantity: Int): java.math.BigDecimal?

    /**
     * Atomically reserve GA level tickets if available capacity exists AND return price.
     * This prevents race conditions by doing price fetch + reservation in one operation.
     *
     * Uses native SQL with RETURNING clause (PostgreSQL).
     *
     * @param sessionId Event session ID
     * @param levelId Level ID to reserve from
     * @param quantity Number of tickets to reserve
     * @return Price if reservation successful, null if failed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
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
    fun reserveGAAndGetPrice(sessionId: Long, levelId: Long, quantity: Int): java.math.BigDecimal?
}

