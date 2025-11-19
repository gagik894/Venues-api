package app.venues.seating.repository

import app.venues.seating.domain.ChartZone
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for zone (section/floor) operations.
 */
@Repository
interface ChartZoneRepository : JpaRepository<ChartZone, Long> {

    /**
     * Find all zones belonging to a chart.
     */
    fun findByChartId(chartId: UUID): List<ChartZone>

    /**
     * Find root zones (no parent) for the chart.
     */
    fun findByChartIdAndParentZoneIsNull(chartId: UUID): List<ChartZone>

    /**
     * Find a zone by its business key within a chart.
     */
    fun findByChartIdAndCode(chartId: UUID, code: String): ChartZone?

    /**
     * Check if zone code exists in chart.
     */
    fun existsByChartIdAndCode(chartId: UUID, code: String): Boolean

    /**
     * Count zones in a chart.
     */
    @Query("SELECT COUNT(z) FROM ChartZone z WHERE z.chart.id = :chartId")
    fun countByChartId(chartId: UUID): Long
}
