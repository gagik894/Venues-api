package app.venues.event.repository

import app.venues.event.domain.ConfigStatus
import app.venues.event.domain.SessionLevelConfig
import org.springframework.data.jpa.repository.JpaRepository
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
     * Find config by session and level identifier
     */
    @Query(
        """
        SELECT lc FROM SessionLevelConfig lc
        WHERE lc.session.id = :sessionId
        AND lc.level.levelIdentifier = :levelIdentifier
    """
    )
    fun findBySessionIdAndLevelIdentifier(sessionId: Long, levelIdentifier: String): SessionLevelConfig?

    /**
     * Find all available level configs for session
     */
    fun findBySessionIdAndStatus(sessionId: Long, status: ConfigStatus): List<SessionLevelConfig>

    /**
     * Find all level configs for session
     */
    fun findBySessionId(sessionId: Long): List<SessionLevelConfig>
}

