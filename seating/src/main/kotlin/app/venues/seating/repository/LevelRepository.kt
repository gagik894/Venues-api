package app.venues.seating.repository

import app.venues.seating.domain.Level
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

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
     * Find levels in a specific seating chart.
     * Uses direct column reference for clean architecture.
     */
    fun findBySeatingChartId(chartId: UUID): List<Level>

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

    /**
     * Finds all parent level IDs of a given seat that are marked as tables.
     * Uses a native recursive CTE to walk up the level hierarchy.
     *
     * @param seatId The ID of the seat
     * @return A list of level IDs
     */
    @Query(
        nativeQuery = true,
        value = """
            WITH RECURSIVE LevelHierarchy AS (
                SELECT 
                    l.id, l.parent_level_id, l.is_table
                FROM levels l
                JOIN seats s ON s.level_id = l.id
                WHERE s.id = :seatId
    
                UNION ALL
    
                -- Recursive step: Join with parent
                SELECT 
                    l.id, l.parent_level_id, l.is_table
                FROM levels l
                JOIN LevelHierarchy h ON l.id = h.parent_level_id
            )
            -- Select only the table IDs from the hierarchy
            SELECT id
            FROM LevelHierarchy
            WHERE is_table = true
        """
    )
    fun findParentTableIdsForSeat(seatId: Long): List<Long>
}

