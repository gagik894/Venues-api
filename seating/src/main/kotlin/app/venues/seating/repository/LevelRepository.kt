package app.venues.seating.repository

import app.venues.seating.domain.Level
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for Level entity operations.
 */
@Repository
interface LevelRepository : JpaRepository<Level, Long> {

    /**
     * Find levels by parent level ID
     */
    fun findByParentLevelId(parentLevelId: Long): List<Level>

    /**
     * Find top-level sections (no parent)
     */
    fun findByParentLevelIsNull(): List<Level>

    /**
     * Find level by identifier
     */
    fun findByLevelIdentifier(identifier: String): Level?

    /**
     * Find levels in a specific seating chart
     * Ordered by level name for consistent display
     */
    @Query(
        """
        SELECT l FROM Level l
        JOIN l.seatingCharts sc
        WHERE sc.id = :chartId
        ORDER BY l.levelName
    """
    )
    fun findBySeatingChartId(chartId: Long): List<Level>

    /**
     * Find GA (General Admission) levels
     */
    @Query(
        """
        SELECT l FROM Level l
        WHERE l.capacity IS NOT NULL
        AND l.capacity > 0
    """
    )
    fun findGeneralAdmissionLevels(): List<Level>
}

