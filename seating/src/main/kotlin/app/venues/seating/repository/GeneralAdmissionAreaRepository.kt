package app.venues.seating.repository

import app.venues.seating.domain.GeneralAdmissionArea
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface GeneralAdmissionAreaRepository : JpaRepository<GeneralAdmissionArea, Long> {
    fun findByZoneId(zoneId: Long): List<GeneralAdmissionArea>

    @Query("SELECT g FROM GeneralAdmissionArea g WHERE g.zone.chart.id = :chartId")
    fun findByChartId(chartId: UUID): List<GeneralAdmissionArea>
}