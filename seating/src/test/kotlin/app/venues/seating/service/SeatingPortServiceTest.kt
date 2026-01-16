package app.venues.seating.service

import app.venues.seating.domain.*
import app.venues.seating.repository.*
import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.MessageSource
import java.util.*

/**
 * Tests for SeatingPortService batch methods.
 * Covers batch fetching operations for GA areas and tables.
 */
@ExtendWith(MockKExtension::class)
class SeatingPortServiceTest {

    private val seatingChartRepository: SeatingChartRepository = mockk(relaxed = true)
    private val chartZoneRepository: ChartZoneRepository = mockk(relaxed = true)
    private val chartSeatRepository: ChartSeatRepository = mockk(relaxed = true)
    private val chartTableRepository: ChartTableRepository = mockk(relaxed = true)
    private val gaAreaRepository: GeneralAdmissionAreaRepository = mockk(relaxed = true)
    private val messageSource: MessageSource = mockk(relaxed = true)

    private lateinit var service: SeatingPortService

    private val venueId: UUID = UUID.randomUUID()
    private val chartId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        service = SeatingPortService(
            seatingChartRepository,
            chartZoneRepository,
            chartSeatRepository,
            chartTableRepository,
            gaAreaRepository,
            messageSource
        )
    }

    // =================================================================================
    // Tests for getGaInfoBatch
    // =================================================================================

    @Test
    fun `getGaInfoBatch returns empty list when input is empty`() {
        val result = service.getGaInfoBatch(emptyList())

        assertTrue(result.isEmpty())
        verify(exactly = 0) { gaAreaRepository.findAllById(any<List<Long>>()) }
    }

    @Test
    fun `getGaInfoBatch fetches multiple GA areas successfully`() {
        // Setup test data
        val chart = SeatingChart(venueId = venueId, name = "Test Chart", width = 2000, height = 2000)
        val zone1 = ChartZone(chart, null, "Zone 1", "Z1", 0.0, 0.0, 0.0, null, null).apply { id = 1L }
        val zone2 = ChartZone(chart, null, "Zone 2", "Z2", 0.0, 0.0, 0.0, null, null).apply { id = 2L }

        val ga1 = GeneralAdmissionArea(
            zone = zone1,
            name = "Pit A",
            code = "GA1",
            capacity = 100,
            categoryKey = "STANDING"
        ).apply { id = 10L }

        val ga2 = GeneralAdmissionArea(
            zone = zone2,
            name = "Pit B",
            code = "GA2",
            capacity = 150,
            categoryKey = "VIP"
        ).apply { id = 20L }

        val ga3 = GeneralAdmissionArea(
            zone = zone1,
            name = "Lawn",
            code = "GA3",
            capacity = 200,
            categoryKey = "GENERAL"
        ).apply { id = 30L }

        every { gaAreaRepository.findAllById(listOf(10L, 20L, 30L)) } returns listOf(ga1, ga2, ga3)

        // Execute
        val result = service.getGaInfoBatch(listOf(10L, 20L, 30L))

        // Assert
        assertEquals(3, result.size)

        val ga1Result = result.find { it.id == 10L }
        assertNotNull(ga1Result)
        assertEquals("GA1", ga1Result!!.code)
        assertEquals("Pit A", ga1Result.name)
        assertEquals(100, ga1Result.capacity)
        assertEquals(1L, ga1Result.zoneId)
        assertEquals("STANDING", ga1Result.categoryKey)

        val ga2Result = result.find { it.id == 20L }
        assertNotNull(ga2Result)
        assertEquals("GA2", ga2Result!!.code)
        assertEquals("Pit B", ga2Result.name)
        assertEquals(150, ga2Result.capacity)
        assertEquals(2L, ga2Result.zoneId)
        assertEquals("VIP", ga2Result.categoryKey)

        val ga3Result = result.find { it.id == 30L }
        assertNotNull(ga3Result)
        assertEquals("GA3", ga3Result!!.code)
        assertEquals("Lawn", ga3Result.name)
        assertEquals(200, ga3Result.capacity)
        assertEquals(1L, ga3Result.zoneId)
        assertEquals("GENERAL", ga3Result.categoryKey)

        verify(exactly = 1) { gaAreaRepository.findAllById(listOf(10L, 20L, 30L)) }
    }

    @Test
    fun `getGaInfoBatch handles missing IDs gracefully`() {
        // Setup test data - only some IDs are found
        val chart = SeatingChart(venueId = venueId, name = "Test Chart", width = 2000, height = 2000)
        val zone = ChartZone(chart, null, "Zone", "Z1", 0.0, 0.0, 0.0, null, null).apply { id = 1L }

        val ga1 = GeneralAdmissionArea(
            zone = zone,
            name = "Existing GA",
            code = "GA1",
            capacity = 100,
            categoryKey = "STANDARD"
        ).apply { id = 10L }

        // Request IDs 10, 999, 888 but only 10 exists
        every { gaAreaRepository.findAllById(listOf(10L, 999L, 888L)) } returns listOf(ga1)

        // Execute
        val result = service.getGaInfoBatch(listOf(10L, 999L, 888L))

        // Assert - only the found GA is returned
        assertEquals(1, result.size)
        assertEquals(10L, result[0].id)
        assertEquals("GA1", result[0].code)
        assertEquals("Existing GA", result[0].name)

        verify(exactly = 1) { gaAreaRepository.findAllById(listOf(10L, 999L, 888L)) }
    }

    @Test
    fun `getGaInfoBatch handles all missing IDs`() {
        // Setup - no IDs are found
        every { gaAreaRepository.findAllById(listOf(999L, 888L)) } returns emptyList()

        // Execute
        val result = service.getGaInfoBatch(listOf(999L, 888L))

        // Assert
        assertTrue(result.isEmpty())
        verify(exactly = 1) { gaAreaRepository.findAllById(listOf(999L, 888L)) }
    }

    // =================================================================================
    // Tests for getTableInfoBatch
    // =================================================================================

    @Test
    fun `getTableInfoBatch returns empty list when input is empty`() {
        val result = service.getTableInfoBatch(emptyList())

        assertTrue(result.isEmpty())
        verify(exactly = 0) { chartTableRepository.findAllById(any<List<Long>>()) }
    }

    @Test
    fun `getTableInfoBatch fetches multiple tables successfully`() {
        // Setup test data
        val chart = SeatingChart(venueId = venueId, name = "Test Chart", width = 2000, height = 2000)
        val zone1 = ChartZone(chart, null, "VIP Zone", "VIP", 0.0, 0.0, 0.0, null, null).apply { id = 1L }
        val zone2 = ChartZone(chart, null, "Main Zone", "MAIN", 0.0, 0.0, 0.0, null, null).apply { id = 2L }

        val table1 = ChartTable(
            zone = zone1,
            tableNumber = "T-1",
            code = "VIP_T1",
            seatCapacity = 4,
            shape = TableShape.ROUND,
            categoryKey = "VIP",
            x = 100.0,
            y = 200.0,
            width = 20.0,
            height = 20.0,
            rotation = 0.0
        ).apply { id = 100L }

        val table2 = ChartTable(
            zone = zone2,
            tableNumber = "T-2",
            code = "MAIN_T2",
            seatCapacity = 6,
            shape = TableShape.RECTANGLE,
            categoryKey = "STANDARD",
            x = 150.0,
            y = 250.0,
            width = 30.0,
            height = 25.0,
            rotation = 45.0
        ).apply { id = 200L }

        val table3 = ChartTable(
            zone = zone1,
            tableNumber = "T-3",
            code = "VIP_T3",
            seatCapacity = 8,
            shape = TableShape.OVAL,
            categoryKey = "PREMIUM",
            x = 300.0,
            y = 400.0,
            width = 40.0,
            height = 30.0,
            rotation = 90.0
        ).apply { id = 300L }

        every { chartTableRepository.findAllById(listOf(100L, 200L, 300L)) } returns listOf(table1, table2, table3)

        // Execute
        val result = service.getTableInfoBatch(listOf(100L, 200L, 300L))

        // Assert
        assertEquals(3, result.size)

        val table1Result = result.find { it.id == 100L }
        assertNotNull(table1Result)
        assertEquals("VIP_T1", table1Result!!.code)
        assertEquals("T-1", table1Result.tableNumber)
        assertEquals(4, table1Result.seatCapacity)
        assertEquals(1L, table1Result.zoneId)
        assertEquals("VIP Zone", table1Result.zoneName)
        assertEquals("VIP", table1Result.categoryKey)

        val table2Result = result.find { it.id == 200L }
        assertNotNull(table2Result)
        assertEquals("MAIN_T2", table2Result!!.code)
        assertEquals("T-2", table2Result.tableNumber)
        assertEquals(6, table2Result.seatCapacity)
        assertEquals(2L, table2Result.zoneId)
        assertEquals("Main Zone", table2Result.zoneName)
        assertEquals("STANDARD", table2Result.categoryKey)

        val table3Result = result.find { it.id == 300L }
        assertNotNull(table3Result)
        assertEquals("VIP_T3", table3Result!!.code)
        assertEquals("T-3", table3Result.tableNumber)
        assertEquals(8, table3Result.seatCapacity)
        assertEquals(1L, table3Result.zoneId)
        assertEquals("VIP Zone", table3Result.zoneName)
        assertEquals("PREMIUM", table3Result.categoryKey)

        verify(exactly = 1) { chartTableRepository.findAllById(listOf(100L, 200L, 300L)) }
    }

    @Test
    fun `getTableInfoBatch handles missing IDs gracefully`() {
        // Setup test data - only some IDs are found
        val chart = SeatingChart(venueId = venueId, name = "Test Chart", width = 2000, height = 2000)
        val zone = ChartZone(chart, null, "Zone", "Z1", 0.0, 0.0, 0.0, null, null).apply { id = 1L }

        val table1 = ChartTable(
            zone = zone,
            tableNumber = "T-1",
            code = "T1",
            seatCapacity = 4,
            shape = TableShape.ROUND,
            categoryKey = "STANDARD",
            x = 100.0,
            y = 200.0,
            width = 20.0,
            height = 20.0,
            rotation = 0.0
        ).apply { id = 100L }

        // Request IDs 100, 999, 888 but only 100 exists
        every { chartTableRepository.findAllById(listOf(100L, 999L, 888L)) } returns listOf(table1)

        // Execute
        val result = service.getTableInfoBatch(listOf(100L, 999L, 888L))

        // Assert - only the found table is returned
        assertEquals(1, result.size)
        assertEquals(100L, result[0].id)
        assertEquals("T1", result[0].code)
        assertEquals("T-1", result[0].tableNumber)
        assertEquals("Zone", result[0].zoneName)

        verify(exactly = 1) { chartTableRepository.findAllById(listOf(100L, 999L, 888L)) }
    }

    @Test
    fun `getTableInfoBatch handles all missing IDs`() {
        // Setup - no IDs are found
        every { chartTableRepository.findAllById(listOf(999L, 888L)) } returns emptyList()

        // Execute
        val result = service.getTableInfoBatch(listOf(999L, 888L))

        // Assert
        assertTrue(result.isEmpty())
        verify(exactly = 1) { chartTableRepository.findAllById(listOf(999L, 888L)) }
    }
}
