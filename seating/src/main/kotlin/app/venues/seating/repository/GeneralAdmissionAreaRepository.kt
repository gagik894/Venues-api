package app.venues.seating.repository

import app.venues.seating.domain.GeneralAdmissionArea
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for general admission area operations.
 */
@Repository
interface GeneralAdmissionAreaRepository : JpaRepository<GeneralAdmissionArea, Long> {

    /**
     * Find GA area by business code within a chart.
     */
    @Query("SELECT g FROM GeneralAdmissionArea g WHERE g.code = :code AND g.zone.chart.id = :chartId")
    fun findByChartIdAndCode(chartId: UUID, code: String): GeneralAdmissionArea?

    /**
     * Find GA areas by zone.
     */
    fun findByZoneId(zoneId: Long): List<GeneralAdmissionArea>

    /**
     * Find all GA areas in a chart.
     */
    @Query("SELECT g FROM GeneralAdmissionArea g WHERE g.zone.chart.id = :chartId")
    fun findByChartId(chartId: UUID): List<GeneralAdmissionArea>

    /**
     * Sum total GA capacity in a chart.
     */
    @Query("SELECT SUM(g.capacity) FROM GeneralAdmissionArea g WHERE g.zone.chart.id = :chartId")
    fun sumCapacityByChartId(chartId: UUID): Long?
}
