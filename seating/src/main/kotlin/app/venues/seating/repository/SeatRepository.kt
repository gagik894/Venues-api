package app.venues.seating.repository

import app.venues.seating.domain.Seat
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository for Seat entity operations.
 */
@Repository
interface SeatRepository : JpaRepository<Seat, Long> {

    /**
     * Find seats by level ID
     */
    fun findByLevelId(levelId: Long, pageable: Pageable): Page<Seat>

    /**
     * Find all seats by level ID
     */
    fun findByLevelId(levelId: Long): List<Seat>

    /**
     * Find seat by level and identifier
     */
    fun findByLevelIdAndSeatIdentifier(levelId: Long, seatIdentifier: String): Seat?

    /**
     * Find seat by identifier only (must be globally unique within chart)
     */
    fun findBySeatIdentifier(seatIdentifier: String): Seat?

    /**
     * Find seats by seating chart
     */
    @Query(
        """
        SELECT s FROM Seat s
        JOIN s.seatingCharts sc
        WHERE sc.id = :chartId
        ORDER BY s.level.levelNumber, s.seatIdentifier
    """
    )
    fun findBySeatingChartId(chartId: Long): List<Seat>

    /**
     * Find seats by type
     */
    fun findBySeatType(seatType: String, pageable: Pageable): Page<Seat>

    /**
     * Count seats in a level
     */
    fun countByLevelId(levelId: Long): Long

    /**
     * Check if seat identifier exists in level
     */
    fun existsByLevelIdAndSeatIdentifier(levelId: Long, seatIdentifier: String): Boolean
}

