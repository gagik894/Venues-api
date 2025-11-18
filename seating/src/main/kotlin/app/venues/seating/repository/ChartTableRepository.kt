package app.venues.seating.repository

import app.venues.seating.domain.ChartTable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ChartTableRepository : JpaRepository<ChartTable, Long> {
    fun findByZoneId(zoneId: Long): List<ChartTable>

    @Query("SELECT t FROM ChartTable t WHERE t.zone.chart.id = :chartId")
    fun findByChartId(chartId: UUID): List<ChartTable>
}