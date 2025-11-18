package app.venues.seating.repository

import app.venues.seating.domain.ChartZone
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

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
     * Find a zone by its immutable Business Key prefix within a chart.
     */
    fun findByChartIdAndCode(chartId: UUID, code: String): ChartZone?
}