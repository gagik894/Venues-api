package app.venues.seating.repository

import app.venues.seating.domain.ChartLandmark
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ChartLandmarkRepository : JpaRepository<ChartLandmark, Long> {
    fun findByChartId(chartId: UUID): List<ChartLandmark>
}