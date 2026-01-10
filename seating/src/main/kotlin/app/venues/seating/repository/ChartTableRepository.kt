package app.venues.seating.repository

import app.venues.seating.domain.ChartTable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for table operations.
 */
@Repository
interface ChartTableRepository : JpaRepository<ChartTable, Long> {

    /**
     * Find table by business code within a chart.
     */
    @Query("SELECT t FROM ChartTable t WHERE t.code = :code AND t.zone.chart.id = :chartId")
    fun findByChartIdAndCode(chartId: UUID, code: String): ChartTable?

    /**
     * Find tables by zone.
     */
    fun findByZoneId(zoneId: Long): List<ChartTable>

    /**
     * Find all tables in a chart.
     */
    @Query("SELECT t FROM ChartTable t WHERE t.zone.chart.id = :chartId")
    fun findByChartId(chartId: UUID): List<ChartTable>
}
