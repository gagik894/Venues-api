package app.venues.ticket.service

import app.venues.seating.api.SeatingApi
import app.venues.ticket.api.TicketApi
import app.venues.ticket.api.dto.TicketDto
import app.venues.ticket.api.mapper.TicketMapper
import app.venues.ticket.domain.Ticket
import app.venues.ticket.domain.TicketType
import app.venues.ticket.repository.TicketRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class TicketGenerationService(
    private val ticketRepository: TicketRepository,
    private val qrCodeService: QRCodeService,
    private val seatingApi: SeatingApi,
    private val ticketMapper: TicketMapper
) : TicketApi {

    @Transactional
    override fun generateTicketsForBookingItem(
        bookingId: UUID,
        bookingItemId: Long,
        eventSessionId: UUID,
        ticketType: String,
        seatId: Long?,
        gaAreaId: Long?,
        tableId: Long?,
        quantity: Int,
        qrCodes: List<String>?
    ): List<TicketDto> {

        val type = TicketType.valueOf(ticketType)

        // Determine max scan count
        val maxScanCount = if (type == TicketType.TABLE) {
            requireNotNull(tableId) { "Table ID required for TABLE ticket type" }
            val tableInfo = seatingApi.getTableInfo(tableId)
                ?: throw IllegalArgumentException("Table not found: $tableId")
            tableInfo.seatCapacity
        } else {
            1
        }

        // Validate QR codes if provided
        if (qrCodes != null) {
            require(qrCodes.size == quantity) {
                "Number of QR codes (${qrCodes.size}) must match quantity ($quantity)"
            }
        }

        val tickets = (0 until quantity).map { index ->
            Ticket(
                bookingId = bookingId,
                bookingItemId = bookingItemId,
                eventSessionId = eventSessionId,
                // Use provided QR code (platform) or generate new one (venue)
                qrCode = qrCodes?.get(index) ?: qrCodeService.generateTicketQrCode(),
                ticketType = type,
                seatId = seatId,
                gaAreaId = gaAreaId,
                tableId = tableId,
                maxScanCount = maxScanCount
            )
        }

        return ticketRepository.saveAll(tickets).map { ticketMapper.toDto(it) }
    }

    @Transactional
    override fun invalidateTicketsForBooking(bookingId: UUID, staffId: UUID, reason: String) {
        val tickets = ticketRepository.findByBookingId(bookingId)
        tickets.forEach { it.invalidate(staffId, reason) }
        ticketRepository.saveAll(tickets)
    }

    @Transactional
    override fun invalidateTicketsForBookingItem(bookingId: UUID, bookingItemId: Long, staffId: UUID, reason: String) {
        val tickets = ticketRepository.findByBookingItemId(bookingItemId)
        // Verify bookingId matches to be safe
        tickets.filter { it.bookingId == bookingId }
            .forEach { it.invalidate(staffId, reason) }
        ticketRepository.saveAll(tickets)
    }
}
