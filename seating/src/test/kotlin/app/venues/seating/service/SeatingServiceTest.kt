package app.venues.seating.service

import app.venues.common.exception.VenuesException
import app.venues.seating.domain.*
import app.venues.seating.mapper.SeatingResponseMapper
import app.venues.seating.model.*
import app.venues.seating.repository.*
import app.venues.venue.api.VenueApi
import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class SeatingServiceTest {

    private val seatingChartRepository: SeatingChartRepository = mockk(relaxed = true)
    private val chartZoneRepository: ChartZoneRepository = mockk(relaxed = true)
    private val chartSeatRepository: ChartSeatRepository = mockk(relaxed = true)
    private val chartTableRepository: ChartTableRepository = mockk(relaxed = true)
    private val gaAreaRepository: GeneralAdmissionAreaRepository = mockk(relaxed = true)
    private val chartLandmarkRepository: ChartLandmarkRepository = mockk(relaxed = true)
    private val eventApi: app.venues.event.api.EventApi = mockk(relaxed = true)
    private val venueApi: VenueApi = mockk(relaxed = true)
    private val mapper = SeatingResponseMapper()

    private lateinit var service: SeatingService

    private val venueId: UUID = UUID.randomUUID()
    private val sourceChartId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        service = spyk(
            SeatingService(
                seatingChartRepository,
                chartZoneRepository,
                chartSeatRepository,
                chartTableRepository,
                gaAreaRepository,
                chartLandmarkRepository,
                eventApi,
                mapper,
                venueApi
            ),
            recordPrivateCalls = true
        )
        every { venueApi.venueExists(venueId) } returns true
        every { seatingChartRepository.existsByVenueIdAndName(any(), any()) } returns false
    }

    @Test
    fun `deleteSeatingChart is blocked when chart in use`() {
        val chartId = UUID.randomUUID()
        val chart = SeatingChart(venueId = venueId, name = "Chart", width = 100, height = 100)
        every { seatingChartRepository.findById(chartId) } returns Optional.of(chart)
        every { eventApi.seatingChartInUse(chartId) } returns true

        assertThrows<VenuesException.ValidationFailure> {
            service.deleteSeatingChart(chartId, venueId)
        }
        verify(exactly = 0) { seatingChartRepository.delete(any()) }
    }

    @Test
    fun `clone chart copies structure and codes`() {
        // Source chart structure
        val sourceChart = SeatingChart(
            venueId = venueId,
            name = "Original",
            width = 2000,
            height = 2000,
            backgroundUrl = "bg.png"
        )
        val zone = ChartZone(
            chart = sourceChart,
            parentZone = null,
            name = "Main",
            code = "Z1",
            x = 10.0,
            y = 20.0,
            rotation = 0.0,
            boundaryPath = null,
            displayColor = "#111111"
        ).apply { id = 1L }
        val table = ChartTable(
            zone = zone,
            tableNumber = "T-1",
            code = "T1",
            seatCapacity = 4,
            categoryKey = "VIP",
            shape = TableShape.ROUND,
            x = 100.0,
            y = 200.0,
            width = 20.0,
            height = 20.0,
            rotation = 0.0
        ).apply { id = 2L }
        val seat = ChartSeat(
            zone = zone,
            table = table,
            rowLabel = "A",
            seatNumber = "1",
            code = "Z1_ROW-A_SEAT-1",
            categoryKey = "VIP",
            isAccessible = false,
            isObstructedView = false,
            x = 11.0,
            y = 22.0,
            rotation = 0.0
        ).apply { id = 3L }
        table.attachSeat(seat)
        zone.addTable(table)
        zone.addSeat(seat)
        val ga = GeneralAdmissionArea(
            zone = zone,
            name = "Pit",
            code = "GA1",
            capacity = 100,
            categoryKey = "GA",
            boundaryPath = null,
            displayColor = "#AAAAAA"
        ).apply { id = 4L }
        zone.addGaArea(ga)
        val landmark = ChartLandmark(
            chart = sourceChart,
            label = "Stage",
            type = LandmarkType.STAGE,
            shapeType = LandmarkShapeType.RECTANGLE,
            x = 5.0,
            y = 6.0,
            width = 50.0,
            height = 10.0,
            rotation = 0.0,
            boundaryPath = null,
            iconKey = null
        ).apply { id = 5L }
        sourceChart.addZone(zone)

        // Mocks
        every { seatingChartRepository.findById(sourceChartId) } returns Optional.of(sourceChart)
        every { chartZoneRepository.findByChartId(sourceChartId) } returns listOf(zone)
        every { chartTableRepository.findByChartId(sourceChartId) } returns listOf(table)
        every { chartSeatRepository.findByChartId(sourceChartId) } returns listOf(seat)
        every { gaAreaRepository.findByChartId(sourceChartId) } returns listOf(ga)
        every { chartLandmarkRepository.findByChartId(sourceChartId) } returns listOf(landmark)

        val savedSlot = slot<SeatingChart>()
        every { seatingChartRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
        every { chartLandmarkRepository.saveAll(any<List<ChartLandmark>>()) } answers { firstArg() }

        val dummyResponse = SeatingChartDetailedResponse(
            id = UUID.randomUUID(),
            venueId = venueId,
            name = "Cloned",
            width = 2000,
            height = 2000,
            backgroundUrl = "bg.png",
            backgroundTransform = null,
            rootZones = emptyList(),
            createdAt = "now",
            updatedAt = "now"
        )
        every { service.getSeatingChartDetailed(any()) } returns dummyResponse

        val result = service.cloneSeatingChart(
            venueId = venueId,
            sourceChartId = sourceChartId,
            request = CloneSeatingChartRequest(name = "Cloned", backgroundUrl = null)
        )

        assertEquals(dummyResponse, result)
        val clonedChart = savedSlot.captured
        assertEquals("Cloned", clonedChart.name)
        assertEquals(sourceChart.width, clonedChart.width)
        assertEquals(sourceChart.height, clonedChart.height)
        assertEquals(sourceChart.backgroundUrl, clonedChart.backgroundUrl)

        // Validate cloned structure
        val clonedZone = clonedChart.zones.single()
        assertEquals(zone.code, clonedZone.code)
        assertEquals(zone.name, clonedZone.name)
        assertEquals(zone.displayColor, clonedZone.displayColor)

        val clonedTable = clonedZone.tables.single()
        assertEquals(table.code, clonedTable.code)
        assertEquals(table.tableNumber, clonedTable.tableNumber)

        val clonedSeat = clonedZone.seats.single()
        assertEquals(seat.code, clonedSeat.code)
        assertEquals(seat.rowLabel, clonedSeat.rowLabel)
        assertEquals(seat.seatNumber, clonedSeat.seatNumber)
        assertSame(clonedTable, clonedSeat.table)

        val clonedGa = clonedZone.gaAreas.single()
        assertEquals(ga.code, clonedGa.code)
        assertEquals(ga.capacity, clonedGa.capacity)

        verify {
            chartLandmarkRepository.saveAll(
                match<List<ChartLandmark>> { list ->
                    list.size == 1 && list[0].label == "Stage"
                }
            )
        }
    }

    @Test
    fun `visual updates adjust fields without touching codes`() {
        val chart = SeatingChart(venueId = venueId, name = "Chart", width = 2000, height = 2000)
        val chartId = chart.id
        val zone = ChartZone(chart, null, "Zone", "Z1", 0.0, 0.0, 0.0, null, null).apply { id = 10L }
        val seat =
            ChartSeat(zone, null, "A", "1", "Z1_ROW-A_SEAT-1", "STD", false, false, 1.0, 2.0, 0.0).apply { id = 11L }
        val table =
            ChartTable(zone, "T-1", "T1", 4, TableShape.SQUARE, "STD", 5.0, 6.0, 10.0, 10.0, 0.0).apply { id = 12L }
        val ga = GeneralAdmissionArea(zone, "GA", "GA1", 50, "GA", null, null).apply { id = 13L }
        val landmark = ChartLandmark(
            chart,
            "Bar",
            LandmarkType.BAR,
            LandmarkShapeType.RECTANGLE,
            1.0,
            1.0,
            2.0,
            2.0,
            0.0,
            null,
            null
        ).apply { id = 14L }
        chart.addZone(zone)
        zone.addSeat(seat)
        zone.addTable(table)
        zone.addGaArea(ga)

        every { seatingChartRepository.findById(chartId) } returns Optional.of(chart)
        every { chartZoneRepository.findAllById(listOf(zone.id!!)) } returns listOf(zone)
        every { chartSeatRepository.findAllById(listOf(seat.id!!)) } returns listOf(seat)
        every { chartTableRepository.findAllById(listOf(table.id!!)) } returns listOf(table)
        every { gaAreaRepository.findAllById(listOf(ga.id!!)) } returns listOf(ga)
        every { chartLandmarkRepository.findAllById(listOf(landmark.id!!)) } returns listOf(landmark)

        every { chartZoneRepository.saveAll(any<List<ChartZone>>()) } answers { firstArg() }
        every { chartSeatRepository.saveAll(any<List<ChartSeat>>()) } answers { firstArg() }
        every { chartTableRepository.saveAll(any<List<ChartTable>>()) } answers { firstArg() }
        every { gaAreaRepository.saveAll(any<List<GeneralAdmissionArea>>()) } answers { firstArg() }
        every { chartLandmarkRepository.saveAll(any<List<ChartLandmark>>()) } answers { firstArg() }
        every { chartLandmarkRepository.deleteAllInBatch(any<List<ChartLandmark>>()) } just Runs

        val dummyResponse = SeatingChartDetailedResponse(
            id = chartId,
            venueId = venueId,
            name = "Chart",
            width = 2000,
            height = 2000,
            backgroundUrl = null,
            backgroundTransform = null,
            rootZones = emptyList(),
            createdAt = "now",
            updatedAt = "now"
        )
        every { service.getSeatingChartDetailed(chartId) } returns dummyResponse

        val request = SeatingChartVisualUpdateRequest(
            zones = listOf(ZoneVisualUpdate(id = zone.id!!, x = 100.0, y = 200.0, displayColor = "#00FF00")),
            seats = listOf(
                SeatVisualUpdate(
                    id = seat.id!!,
                    x = 9.0,
                    y = 8.0,
                    isAccessible = true,
                    categoryKey = "NEWCAT"
                )
            ),
            tables = listOf(TableVisualUpdate(id = table.id!!, rotation = 45.0, shape = "RECTANGLE")),
            gaAreas = listOf(GaAreaVisualUpdate(id = ga.id!!, capacity = 80)),
            landmarks = LandmarkUpdates(
                upserts = listOf(
                    LandmarkUpsert(
                        id = landmark.id,
                        label = "Bar Updated",
                        type = "BAR",
                        shapeType = "RECTANGLE",
                        x = 2.0,
                        y = 3.0,
                        width = 3.0,
                        height = 4.0,
                        rotation = 5.0,
                        boundaryPath = null,
                        iconKey = "icon-bar"
                    )
                ),
                deleteIds = emptyList()
            )
        )

        val response = service.updateVisuals(chartId, venueId, request)
        assertEquals(dummyResponse, response)

        assertEquals(100.0, zone.x)
        assertEquals(200.0, zone.y)
        assertEquals("#00FF00", zone.displayColor)

        assertEquals(9.0, seat.x)
        assertEquals(8.0, seat.y)
        assertTrue(seat.isAccessible)
        assertEquals("NEWCAT", seat.categoryKey)

        assertEquals(45.0, table.rotation)
        assertEquals(TableShape.RECTANGLE, table.shape)

        assertEquals(80, ga.capacity)

        assertEquals("Bar Updated", landmark.label)
        assertEquals(2.0, landmark.x)
        assertEquals("icon-bar", landmark.iconKey)
    }

    @Test
    fun `layout replace is disabled`() {
        assertThrows<VenuesException.ValidationFailure> {
            service.replaceSeatingChartLayout(
                chartId = UUID.randomUUID(),
                venueId = venueId,
                request = SeatingChartLayoutRequest(
                    chart = SeatingChartRequest(name = "X", width = 100, height = 100),
                    zones = listOf(
                        ZoneLayoutRequest(name = "Z", code = "Z", x = 0.0, y = 0.0, rotation = 0.0)
                    )
                )
            )
        }
    }
}

