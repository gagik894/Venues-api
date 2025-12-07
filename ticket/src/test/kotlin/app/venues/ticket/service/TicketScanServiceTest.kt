package app.venues.ticket.service

import app.venues.booking.api.BookingApi
import app.venues.booking.api.dto.BookingResponse
import app.venues.event.api.EventApi
import app.venues.event.api.dto.EventSessionDto
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.SeatInfoDto
import app.venues.seating.api.dto.SectionInfoDto
import app.venues.seating.api.dto.TableInfoDto
import app.venues.ticket.domain.Ticket
import app.venues.ticket.domain.TicketStatus
import app.venues.ticket.domain.TicketType
import app.venues.ticket.repository.TicketRepository
import app.venues.ticket.repository.TicketScanRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class TicketScanServiceTest {

    private val ticketRepository = mockk<TicketRepository>()
    private val ticketScanRepository = mockk<TicketScanRepository>()
    private val scannerSessionRepository = mockk<app.venues.ticket.repository.ScannerSessionRepository>()
    private val eventApi = mockk<EventApi>()
    private val bookingApi = mockk<BookingApi>()
    private val seatingApi = mockk<SeatingApi>()

    private val service = TicketScanService(
        ticketRepository = ticketRepository,
        eventApi = eventApi,
        seatingApi = seatingApi,
        bookingApi = bookingApi,
        scannerSessionRepository = scannerSessionRepository
    )

    @Test
    fun `should successfully scan valid ticket`() {
        // Given
        val qrCode = "TKT-VALID"
        val ticket = Ticket(
            bookingId = UUID.randomUUID(),
            bookingItemId = 1L,
            eventSessionId = UUID.randomUUID(),
            qrCode = qrCode,
            ticketType = TicketType.SEAT,
            seatId = 100L,
            gaAreaId = null,
            tableId = null,
            maxScanCount = 1
        )
        val scannerSessionId = UUID.randomUUID()

        every { ticketRepository.findByQrCode(qrCode) } returns ticket
        every { ticketRepository.save(any()) } returns ticket

        // Mock Scanner Session
        val scannerSession = mockk<app.venues.ticket.domain.ScannerSession>()
        every { scannerSession.isValid() } returns true
        every { scannerSession.eventId } returns ticket.eventSessionId
        every { scannerSessionRepository.findById(scannerSessionId) } returns Optional.of(scannerSession)

        // Mock external APIs
        every { eventApi.getEventSessionInfo(any()) } returns EventSessionDto(
            sessionId = UUID.randomUUID(),
            eventId = ticket.eventSessionId, // Match scanner session
            venueId = UUID.randomUUID(),
            eventTitle = "Concert",
            eventDescription = "Desc",
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(7200)
        )
        every { bookingApi.getBookingById(any()) } returns mockk<BookingResponse> {
            every { customerName } returns "John Doe"
        }
        every { seatingApi.getSeatInfo(100L) } returns SeatInfoDto(
            id = 100L,
            code = "A1",
            seatNumber = "1",
            rowLabel = "A",
            zoneId = 1L,
            zoneName = "VIP",
            categoryKey = "CAT"
        )

        every { seatingApi.getZoneHierarchy(1L) } returns listOf(
            SectionInfoDto(10L, "VIP_ZONE", "VIP Area"),
            SectionInfoDto(1L, "ROW_A", "Row A")
        )

        // When
        val result = service.scanTicket(qrCode, scannerSessionId, null, null)

        // Then
        assertTrue(result.success)
        assertEquals("John Doe", result.ticketInfo?.customerName)
        assertEquals(listOf("VIP Area", "Row A"), result.ticketInfo?.seatDetail?.sectionNames)
        assertEquals("A", result.ticketInfo?.seatDetail?.rowLabel)
        assertEquals("1", result.ticketInfo?.seatDetail?.seatNumber)
        assertEquals(TicketStatus.SCANNED, ticket.status)
        assertEquals(1, ticket.getScanCount())
        assertNotNull(result.scanTimestamp)
        assertEquals(result.scanTimestamp, result.lastScanTimestamp)
    }

    @Test
    fun `should fail if ticket already scanned`() {
        // Given
        val qrCode = "TKT-USED"
        val ticket = Ticket(
            bookingId = UUID.randomUUID(),
            bookingItemId = 1L,
            eventSessionId = UUID.randomUUID(),
            qrCode = qrCode,
            ticketType = TicketType.SEAT,
            seatId = 100L,
            gaAreaId = null,
            tableId = null,
            maxScanCount = 1
        )
        ticket.scan(UUID.randomUUID()) // Already scanned

        val scannerSessionId = UUID.randomUUID()

        every { ticketRepository.findByQrCode(qrCode) } returns ticket

        // Mock Scanner Session
        val scannerSession = mockk<app.venues.ticket.domain.ScannerSession>()
        every { scannerSession.isValid() } returns true
        every { scannerSession.eventId } returns ticket.eventSessionId
        every { scannerSessionRepository.findById(scannerSessionId) } returns Optional.of(scannerSession)

        // Mock Event Session (needed for validation)
        every { eventApi.getEventSessionInfo(any()) } returns EventSessionDto(
            sessionId = UUID.randomUUID(),
            eventId = ticket.eventSessionId,
            eventTitle = "Concert",
            eventDescription = "Desc",
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(7200),
            venueId = UUID.randomUUID()
        )

        // When
        val result = service.scanTicket(qrCode, scannerSessionId, null, null)

        // Then
        assertFalse(result.success, "Expected failure but got success: ${result.message}")
        assertTrue(result.message?.contains("already scanned", ignoreCase = true) == true)
        assertNotNull(result.lastScanTimestamp)
    }

    @Test
    fun `should fail if ticket invalidated`() {
        // Given
        val qrCode = "TKT-INVALID"
        val ticket = Ticket(
            bookingId = UUID.randomUUID(),
            bookingItemId = 1L,
            eventSessionId = UUID.randomUUID(),
            qrCode = qrCode,
            ticketType = TicketType.SEAT,
            seatId = 100L,
            gaAreaId = null,
            tableId = null,
            maxScanCount = 1
        )
        ticket.invalidate(UUID.randomUUID(), "Fraud")

        val scannerSessionId = UUID.randomUUID()

        every { ticketRepository.findByQrCode(qrCode) } returns ticket

        // Mock Scanner Session
        val scannerSession = mockk<app.venues.ticket.domain.ScannerSession>()
        every { scannerSession.isValid() } returns true
        every { scannerSession.eventId } returns ticket.eventSessionId
        every { scannerSessionRepository.findById(scannerSessionId) } returns Optional.of(scannerSession)

        // Mock Event Session (needed for validation)
        every { eventApi.getEventSessionInfo(any()) } returns EventSessionDto(
            sessionId = UUID.randomUUID(),
            eventId = ticket.eventSessionId,
            eventTitle = "Concert",
            eventDescription = "Desc",
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(7200),
            venueId = UUID.randomUUID()
        )

        // When
        val result = service.scanTicket(qrCode, scannerSessionId, null, null)

        // Then
        assertFalse(result.success)
        assertTrue(result.message?.contains("invalidated", ignoreCase = true) == true)
    }

    @Test
    fun `should allow multi-scan for table ticket`() {
        // Given
        val qrCode = "TKT-TABLE"
        val ticket = Ticket(
            bookingId = UUID.randomUUID(),
            bookingItemId = 1L,
            eventSessionId = UUID.randomUUID(),
            qrCode = qrCode,
            ticketType = TicketType.TABLE,
            seatId = null,
            gaAreaId = null,
            tableId = 300L,
            maxScanCount = 4
        )
        // Simulate 2 previous scans
        ticket.scan(UUID.randomUUID())
        ticket.scan(UUID.randomUUID())

        val scannerSessionId = UUID.randomUUID()

        every { ticketRepository.findByQrCode(qrCode) } returns ticket
        every { ticketRepository.save(any()) } returns ticket

        // Mock Scanner Session
        val scannerSession = mockk<app.venues.ticket.domain.ScannerSession>()
        every { scannerSession.isValid() } returns true
        every { scannerSession.eventId } returns ticket.eventSessionId
        every { scannerSessionRepository.findById(scannerSessionId) } returns Optional.of(scannerSession)

        // Mock external APIs
        every { eventApi.getEventSessionInfo(any()) } returns EventSessionDto(
            sessionId = UUID.randomUUID(),
            eventId = ticket.eventSessionId, // Match scanner session
            venueId = UUID.randomUUID(),
            eventTitle = "Concert",
            eventDescription = "Desc",
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(7200)
        )
        every { bookingApi.getBookingById(any()) } returns mockk<BookingResponse> {
            every { customerName } returns "John Doe"
        }
        every { seatingApi.getTableInfo(300L) } returns TableInfoDto(
            id = 300L,
            code = "T1",
            tableNumber = "1",
            seatCapacity = 4,
            zoneId = 1L,
            zoneName = "VIP",
            categoryKey = "CAT"
        )

        every { seatingApi.getZoneHierarchy(1L) } returns listOf(
            SectionInfoDto(10L, "VIP_ZONE", "VIP Area")
        )

        // When
        val result = service.scanTicket(qrCode, scannerSessionId, null, null)

        // Then
        assertTrue(result.success)
        assertEquals(3, ticket.getScanCount())
        assertEquals(TicketStatus.VALID, ticket.status) // Still valid because count < max
    }

    @Test
    fun `should fail if ticket belongs to different event`() {
        // Given
        val qrCode = "TKT-MISMATCH"
        val eventSessionId = UUID.randomUUID()
        val scannerEventId = UUID.randomUUID() // Different event

        val ticket = Ticket(
            bookingId = UUID.randomUUID(),
            bookingItemId = 1L,
            eventSessionId = eventSessionId,
            qrCode = qrCode,
            ticketType = TicketType.SEAT,
            seatId = 1L, // Required for validation
            maxScanCount = 1
        )
        val scannerSessionId = UUID.randomUUID()

        every { ticketRepository.findByQrCode(qrCode) } returns ticket

        // Mock Scanner Session
        val scannerSession = mockk<app.venues.ticket.domain.ScannerSession>()
        every { scannerSession.isValid() } returns true
        every { scannerSession.eventId } returns scannerEventId
        every { scannerSessionRepository.findById(scannerSessionId) } returns Optional.of(scannerSession)

        // Mock Event Session
        every { eventApi.getEventSessionInfo(any()) } returns EventSessionDto(
            sessionId = UUID.randomUUID(),
            eventId = UUID.randomUUID(), // Different from scannerEventId
            eventTitle = "Other Event",
            eventDescription = "Desc",
            currency = "USD",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(7200),
            venueId = UUID.randomUUID()
        )

        // When
        val result = service.scanTicket(qrCode, scannerSessionId, null, null)

        // Then
        assertFalse(result.success)
        assertEquals("Ticket belongs to a different event", result.message)
    }
}
