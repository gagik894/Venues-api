package app.venues.seating.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class SeatCodeFormatterTest {

    @Test
    fun `buildSeatCode composes hierarchy`() {
        val chart = SeatingChart(
            venueId = UUID.randomUUID(),
            name = "Test",
            width = 500,
            height = 500
        )
        val parent = ChartZone(
            chart = chart,
            name = "Right Tribune",
            code = "RIGHT_TRIBUNE",
            x = 0.0,
            y = 0.0
        )
        chart.addZone(parent)

        val child = ChartZone(
            chart = chart,
            parentZone = parent,
            name = "Sector 5",
            code = "SECTOR5",
            x = 10.0,
            y = 10.0
        )
        parent.addChildZone(child)

        val code = SeatCodeFormatter.buildSeatCode(child, "Row 10", "Seat 15")

        assertEquals("RIGHT_TRIBUNE_SECTOR5_ROW-ROW10_SEAT-SEAT15", code)
    }
}
