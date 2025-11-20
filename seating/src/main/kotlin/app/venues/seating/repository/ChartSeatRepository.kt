package app.venues.seating.repository

import app.venues.seating.domain.ChartSeat
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Seat entity operations.
 */
@Repository
interface ChartSeatRepository : JpaRepository<ChartSeat, Long> {

    /**
     * Find seats by Zone ID.
     */
    fun findByZoneId(zoneId: Long): List<ChartSeat>
    fun findByZoneId(zoneId: Long, pageable: Pageable): Page<ChartSeat>

    /**
     * Find seats by table ID.
     */
    fun findByTableId(tableId: Long): List<ChartSeat>

    /**
     * Optimized batch fetch.
     */
    fun findByZoneIdIn(zoneIds: List<Long>): List<ChartSeat>

    /**
     * Lookup by full business key.
     */
    @Query("SELECT s FROM ChartSeat s WHERE s.code = :code AND s.zone.chart.id = :chartId")
    fun findByChartIdAndCode(chartId: UUID, code: String): ChartSeat?

    fun findByCode(code: String): ChartSeat?

    /**
     * Find all seats belonging to a specific chart.
     */
    @Query("SELECT s FROM ChartSeat s WHERE s.zone.chart.id = :chartId")
    fun findByChartId(chartId: UUID): List<ChartSeat>

    /**
     * Check if seat code exists in zone.
     */
    fun existsByZoneIdAndCode(zoneId: Long, code: String): Boolean

    /**
     * Count seats in a chart.
     */
    @Query("SELECT COUNT(s) FROM ChartSeat s WHERE s.zone.chart.id = :chartId")
    fun countByZoneChartId(chartId: UUID): Long
}
