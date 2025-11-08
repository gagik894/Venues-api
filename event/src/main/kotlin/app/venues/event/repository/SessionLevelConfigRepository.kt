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
     * Find config by session and level
     */
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
     *
     * @param sessionId Event session ID
     * @param levelId Level ID to reserve from
     * @param quantity Number of tickets to reserve
     * @return Number of rows updated (0 or 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE SessionLevelConfig slc
        SET slc.soldCount = slc.soldCount + :quantity
        WHERE slc.session.id = :sessionId
        AND slc.levelId = :levelId
        AND slc.status = app.venues.event.domain.ConfigStatus.AVAILABLE
        AND (slc.capacity - slc.soldCount) >= :quantity
    """
    )
    fun reserveGATicketsIfAvailable(sessionId: Long, levelId: Long, quantity: Int): Int
}

