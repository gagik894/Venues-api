package app.venues.ticket.service

import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.TableInfoDto
import app.venues.ticket.api.dto.TicketDto
import app.venues.ticket.api.mapper.TicketMapper
import app.venues.ticket.domain.Ticket
import app.venues.ticket.domain.TicketStatus
import app.venues.ticket.domain.TicketType
import app.venues.ticket.repository.TicketRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class TicketGenerationServiceTest {

    private val ticketRepository = mockk<TicketRepository>()
    private val qrCodeService = mockk<QRCodeService>()
    private val seatingApi = mockk<SeatingApi>()
    private val ticketMapper = mockk<TicketMapper>()

    private val service = TicketGenerationService(
        ticketRepository = ticketRepository,
        qrCodeService = qrCodeService,
        seatingApi = seatingApi,
        ticketMapper = ticketMapper
    )

    @Test
    fun `should generate single seat ticket`() {
        // Given
        val bookingId = UUID.randomUUID()
        val bookingItemId = 100L
        val eventSessionId = UUID.randomUUID()
        val seatId = 200L

        every { qrCodeService.generateTicketQrCode() } returns "TKT-123"
        val slot = slot<List<Ticket>>()
        every { ticketRepository.saveAll(capture(slot)) } answers {
            slot.captured
        }
        every { ticketMapper.toDto(any()) } answers {
            val t = firstArg<Ticket>()
            TicketDto(
                id = t.id,
                qrCode = t.qrCode,
                ticketType = t.ticketType.name,
                status = t.status.name,
                maxScanCount = t.maxScanCount,
                scanCount = t.getScanCount(),
                remainingScans = t.getRemainingScans()
            )
        }

        // When
        val result = service.generateTicketsForBookingItem(
            bookingId = bookingId,
            bookingItemId = bookingItemId,
            eventSessionId = eventSessionId,
            ticketType = "SEAT",
            seatId = seatId,
            gaAreaId = null,
            tableId = null,
            quantity = 1,
            qrCodes = null
        )

        // Then
        assertEquals(1, result.size)
        assertEquals("TKT-123", result[0].qrCode)
        assertEquals("SEAT", result[0].ticketType)
        assertEquals(1, result[0].maxScanCount)

        val savedTicket = slot.captured[0]
        assertEquals(seatId, savedTicket.seatId)
        assertEquals(TicketType.SEAT, savedTicket.ticketType)
    }

    @Test
    fun `should generate table ticket with capacity as max scans`() {
        // Given
        val bookingId = UUID.randomUUID()
        val bookingItemId = 101L
        val eventSessionId = UUID.randomUUID()
        val tableId = 300L
        val tableCapacity = 4

        every { qrCodeService.generateTicketQrCode() } returns "TKT-TABLE"
        every { seatingApi.getTableInfo(tableId) } returns TableInfoDto(
            id = tableId,
            code = "T1",
            tableNumber = "1",
            seatCapacity = tableCapacity,
            zoneId = 1L,
            zoneName = "Zone",
            categoryKey = "CAT"
        )

        val slot = slot<List<Ticket>>()
        every { ticketRepository.saveAll(capture(slot)) } answers {
            slot.captured
        }
        every { ticketMapper.toDto(any()) } answers {
            val t = firstArg<Ticket>()
            TicketDto(
                id = t.id,
                qrCode = t.qrCode,
                ticketType = t.ticketType.name,
                status = t.status.name,
                maxScanCount = t.maxScanCount,
                scanCount = t.getScanCount(),
                remainingScans = t.getRemainingScans()
            )
        }

        // When
        val result = service.generateTicketsForBookingItem(
            bookingId = bookingId,
            bookingItemId = bookingItemId,
            eventSessionId = eventSessionId,
            ticketType = "TABLE",
            seatId = null,
            gaAreaId = null,
            tableId = tableId,
            quantity = 1,
            qrCodes = null
        )

        // Then
        assertEquals(1, result.size)
        assertEquals("TABLE", result[0].ticketType)
        assertEquals(tableCapacity, result[0].maxScanCount)

        val savedTicket = slot.captured[0]
        assertEquals(tableId, savedTicket.tableId)
    }

    @Test
    fun `should fail if table not found`() {
        // Given
        val tableId = 999L
        every { seatingApi.getTableInfo(tableId) } returns null

        // When/Then
        assertThrows<IllegalArgumentException> {
            service.generateTicketsForBookingItem(
                bookingId = UUID.randomUUID(),
                bookingItemId = 1L,
                eventSessionId = UUID.randomUUID(),
                ticketType = "TABLE",
                seatId = null,
                gaAreaId = null,
                tableId = tableId,
                quantity = 1,
                qrCodes = null
            )
        }
    }

    @Test
    fun `should invalidate tickets for booking`() {
        // Given
        val bookingId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        val reason = "Cancelled"

        val ticket1 = Ticket(
            bookingId = bookingId,
            bookingItemId = 1L,
            eventSessionId = UUID.randomUUID(),
            qrCode = "T1",
            ticketType = TicketType.SEAT,
            seatId = 100L,
            maxScanCount = 1
        )
        val ticket2 = Ticket(
            bookingId = bookingId,
            bookingItemId = 2L,
            eventSessionId = UUID.randomUUID(),
            qrCode = "T2",
            ticketType = TicketType.SEAT,
            seatId = 101L,
            maxScanCount = 1
        )

        every { ticketRepository.findByBookingId(bookingId) } returns listOf(ticket1, ticket2)
        every { ticketRepository.saveAll(any<List<Ticket>>()) } returns listOf(ticket1, ticket2)

        // When
        service.invalidateTicketsForBooking(bookingId, staffId, reason)

        // Then
        assertEquals(TicketStatus.INVALIDATED, ticket1.status)
        assertEquals(staffId, ticket1.invalidatedByStaffId)
        assertEquals(reason, ticket1.invalidationReason)

        assertEquals(TicketStatus.INVALIDATED, ticket2.status)

        verify {
            ticketRepository.saveAll(match<List<Ticket>> { tickets ->
                tickets.size == 2 && tickets.all { it.status == TicketStatus.INVALIDATED }
            })
        }
    }


    @Test
    fun `should invalidate tickets for booking item`() {
        // Given
        val bookingId = UUID.randomUUID()
        val bookingItemId = 1L
        val staffId = UUID.randomUUID()
        val reason = "Refunded Item"

        val ticket1 = Ticket(
            bookingId = bookingId,
            bookingItemId = bookingItemId,
            eventSessionId = UUID.randomUUID(),
            qrCode = "T1",
            ticketType = TicketType.SEAT,
            seatId = 100L,
            maxScanCount = 1
        )
        // Ticket for another item in same booking (should NOT be invalidated)
        val ticket2 = Ticket(
            bookingId = bookingId,
            bookingItemId = 2L,
            eventSessionId = UUID.randomUUID(),
            qrCode = "T2",
            ticketType = TicketType.SEAT,
            seatId = 101L,
            maxScanCount = 1
        )

        every { ticketRepository.findByBookingItemId(bookingItemId) } returns listOf(ticket1)
        every { ticketRepository.saveAll(any<List<Ticket>>()) } returns listOf(ticket1)

        // When
        service.invalidateTicketsForBookingItem(bookingId, bookingItemId, staffId, reason)

        // Then
        assertEquals(TicketStatus.INVALIDATED, ticket1.status)
        assertEquals(staffId, ticket1.invalidatedByStaffId)
        assertEquals(reason, ticket1.invalidationReason)

        assertEquals(TicketStatus.VALID, ticket2.status) // Should remain valid

        verify {
            ticketRepository.saveAll(match<List<Ticket>> { tickets ->
                tickets.size == 1 && tickets[0].qrCode == "T1" && tickets[0].status == TicketStatus.INVALIDATED
            })
        }
    }

    @Test
    fun `should use provided qr codes for bulk generation`() {
        // Given
        val bookingId = UUID.randomUUID()
        val bookingItemId = 1L
        val eventSessionId = UUID.randomUUID()
        val qrCodes = listOf("QR-1", "QR-2", "QR-3")
        val quantity = 3

        val slot = slot<List<Ticket>>()
        every { ticketRepository.saveAll(capture(slot)) } answers {
            slot.captured
        }
        every { ticketMapper.toDto(any()) } returns mockk()

        // When
        service.generateTicketsForBookingItem(
            bookingId = bookingId,
            bookingItemId = bookingItemId,
            eventSessionId = eventSessionId,
            ticketType = "GA",
            seatId = null,
            gaAreaId = 10L,
            tableId = null,
            quantity = quantity,
            qrCodes = qrCodes
        )

        // Then
        val savedTickets = slot.captured
        assertEquals(3, savedTickets.size)
        assertEquals("QR-1", savedTickets[0].qrCode)
        assertEquals("QR-2", savedTickets[1].qrCode)
        assertEquals("QR-3", savedTickets[2].qrCode)
    }
}
