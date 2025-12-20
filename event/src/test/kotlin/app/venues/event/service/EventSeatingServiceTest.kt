package app.venues.event.service

import app.venues.event.domain.*
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class EventSeatingServiceTest {

    private val seatingApi: SeatingApi = mockk()
    private val seatConfigRepository: SessionSeatConfigRepository = mockk(relaxed = true)
    private val tableConfigRepository: SessionTableConfigRepository = mockk(relaxed = true)
    private val gaConfigRepository: SessionGAConfigRepository = mockk(relaxed = true)
    private val sessionCapacityService: SessionCapacityService = mockk(relaxed = true)
    private val seatConfigSparseService: SeatConfigSparseService = mockk(relaxed = true)

    private val eventSeatingService = EventSeatingService(
        seatingApi,
        seatConfigRepository,
        tableConfigRepository,
        gaConfigRepository,
        sessionCapacityService,
        seatConfigSparseService
    )

    private val event = Event(title = "Test", venueId = UUID.randomUUID())
    // Use Instant.now() or mocked time for session
    private val session = EventSession(event = event, startTime = java.time.Instant.now(), endTime = java.time.Instant.now())
    private val chartId = UUID.randomUUID()
    private val template = EventPriceTemplate(event = event, templateName = "VIP", price = BigDecimal.TEN, color = "#FFFFFF")

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    @Test
    fun `generateConfigsForSession should NOT create rows for seats (Sparse Matrix)`() {
        // Given
        event.priceTemplates.add(template)
        val structure = SeatingChartStructureDto(
            chartId = chartId,
            chartName = "Chart",
            width = 100, height = 100,
            zones = emptyList(),
            seats = listOf(
                SeatDto(
                    id = 1, zoneId = 10, tableId = null, code = "S1",
                    rowLabel = "A", seatNumber = "1", categoryKey = "VIP",
                    isAccessible = false, isObstructed = false,
                    x = 10.0, y = 10.0, rotation = 0.0
                )
            ),
            tables = emptyList(),
            gaAreas = emptyList()
        )
        every { seatingApi.getChartStructure(chartId) } returns structure

        // When
        eventSeatingService.generateConfigsForSession(session, chartId, event.priceTemplates)

        // Then
        // Verify we NEVER called saveAll for seats
        verify(exactly = 0) { seatConfigRepository.saveAll(any<List<SessionSeatConfig>>()) }
        
        // But logic should proceed to capacity recalc
        verify { sessionCapacityService.recalculateForSession(session) }
    }

    @Test
    fun `generateConfigsForSession should create rows for tables and GA`() {
        // Given
        val structure = SeatingChartStructureDto(
            chartId = chartId,
            chartName = "Chart",
            width = 100, height = 100,
            zones = emptyList(),
            seats = emptyList(),
            tables = listOf(
                TableDto(
                    id = 2, zoneId = 50, tableNumber = "T1", code = "T1",
                    shape = "RECTANGLE", seatCapacity = 4, categoryKey = "VIP",
                    x = 50.0, y = 50.0, width = 10.0, height = 10.0, rotation = 0.0
                )
            ),
            gaAreas = listOf(
                GaAreaDto(
                    id = 3, zoneId = 0, name = "GA1", code = "GA1",
                    capacity = 100, categoryKey = "General",
                    boundaryPath = null, displayColor = null
                )
            )
        )
        every { seatingApi.getChartStructure(chartId) } returns structure

        // When
        eventSeatingService.generateConfigsForSession(session, chartId, emptyList())

        // Then
        // Use slots to capture and verify
        val tablesSlot = slot<List<SessionTableConfig>>()
        verify { tableConfigRepository.saveAll(capture(tablesSlot)) }
        assertTrue(tablesSlot.captured.size == 1)

        val gaSlot = slot<List<SessionGAConfig>>()
        verify { gaConfigRepository.saveAll(capture(gaSlot)) }
        assertTrue(gaSlot.captured.size == 1)
    }

    @Test
    fun `ensurePriceTemplatesForChart should create new templates for unknown categories`() {
        // Given
        val structure = SeatingChartStructureDto(
            chartId = chartId,
            chartName = "Chart",
            width = 100, height = 100,
            zones = emptyList(),
            seats = listOf(
                SeatDto(
                    id = 1, zoneId = 0, tableId = null, code = "S1",
                    rowLabel = "A", seatNumber = "1", categoryKey = "NEW_CATEGORY",
                    isAccessible = false, isObstructed = false,
                    x = 0.0, y = 0.0, rotation = 0.0
                )
            ),
            tables = emptyList(),
            gaAreas = emptyList()
        )
        every { seatingApi.getChartStructure(chartId) } returns structure
        
        assertTrue(event.priceTemplates.none { it.templateName == "NEW_CATEGORY" })

        // When
        eventSeatingService.ensurePriceTemplatesForChart(event, chartId)

        // Then
        assertTrue(event.priceTemplates.any { it.templateName == "NEW_CATEGORY" })
        assertTrue(event.priceTemplates.any { it.isAnchor })
    }
}
