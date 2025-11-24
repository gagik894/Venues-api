package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionGAConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for SessionGAConfig entity operations.
 * Manages General Admission area configurations for event sessions.
 */
@Repository
interface SessionGAConfigRepository : JpaRepository<SessionGAConfig, Long> {

    /**
     * Find config by session and GA area ID with eagerly loaded priceTemplate.
     */
    @Query(
        """
        SELECT gc FROM SessionGAConfig gc
        LEFT JOIN FETCH gc.priceTemplate
        WHERE gc.session.id = :sessionId
        AND gc.gaAreaId = :gaAreaId
    """
    )
    fun findBySessionIdAndGaAreaId(sessionId: UUID, gaAreaId: Long): SessionGAConfig?

    /**
     * Find all GA configs for a list of GA area IDs.
     */
    @Query(
        """
        SELECT gc FROM SessionGAConfig gc
        LEFT JOIN FETCH gc.priceTemplate
        WHERE gc.session.id = :sessionId
        AND gc.gaAreaId IN :gaAreaIds
    """
    )
    fun findBySessionIdAndGaAreaIdIn(sessionId: UUID, gaAreaIds: List<Long>): List<SessionGAConfig>

    /**
     * Find all available GA configs for session.
     */
    fun findBySessionIdAndStatus(sessionId: UUID, status: ConfigStatus): List<SessionGAConfig>

    /**
     * Find all GA configs for session.
     */
    fun findBySessionId(sessionId: UUID): List<SessionGAConfig>

    /**
     * Calculate total GA capacity for a session.
     */
    @Query(
        """
        SELECT COALESCE(SUM(COALESCE(gc.capacity, 0)), 0)
        FROM SessionGAConfig gc
        WHERE gc.session.id = :sessionId
    """
    )
    fun sumCapacityBySessionId(sessionId: UUID): Long

    /**
     * Get GA area price if available capacity exists.
     */
    @Query(
        """
        SELECT pt.price
        FROM SessionGAConfig gc
        JOIN gc.priceTemplate pt
        WHERE gc.session.id = :sessionId
        AND gc.gaAreaId = :gaAreaId
        AND gc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
        AND (gc.capacity - gc.soldCount) >= :quantity
        AND gc.priceTemplate IS NOT NULL
    """
    )
    fun getGAPriceIfAvailable(sessionId: UUID, gaAreaId: Long, quantity: Int): java.math.BigDecimal?

    /**
     * Atomically reserve GA tickets if available capacity exists AND return price.
     * Prevents race conditions by doing price fetch + reservation in one operation.
     */
    @Query(
        nativeQuery = true,
        value = """
        WITH updated AS (
            UPDATE session_level_configs
            SET sold_count = sold_count + :quantity
            WHERE session_id = :sessionId
            AND ga_area_id = :gaAreaId
            AND status = 'AVAILABLE'
            AND (capacity - sold_count) >= :quantity
            RETURNING price_template_id
        )
        SELECT pt.price
        FROM updated u
        JOIN event_price_templates pt ON pt.id = u.price_template_id
    """
    )
    fun reserveGAAndGetPrice(sessionId: UUID, gaAreaId: Long, quantity: Int): java.math.BigDecimal?

    /**
     * Atomically adjust the sold count for a GA area.
     * Fails if the new total exceeds capacity or goes below zero.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionGAConfig gc
        SET gc.soldCount = gc.soldCount + :quantityDelta
        WHERE gc.session.id = :sessionId
        AND gc.gaAreaId = :gaAreaId
        AND (gc.soldCount + :quantityDelta) >= 0
        AND (gc.soldCount + :quantityDelta) <= gc.capacity
    """
    )
    fun adjustGATickets(sessionId: UUID, gaAreaId: Long, quantityDelta: Int): Int

    /**
     * Check if any configs exist for the session.
     */
    fun existsBySessionId(sessionId: UUID): Boolean
}

