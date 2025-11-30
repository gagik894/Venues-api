package app.venues.ticket.service

import app.venues.seating.api.SeatingApi
import app.venues.ticket.api.TicketApi
import app.venues.ticket.api.dto.TicketDto
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
    private val seatingApi: SeatingApi
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
        qrCode: String?
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

        val tickets = (1..quantity).map {
            Ticket(
                bookingId = bookingId,
                bookingItemId = bookingItemId,
                eventSessionId = eventSessionId,
                // Use provided QR code (platform) or generate new one (venue)
                // Note: If quantity > 1 (e.g. GA), we generate unique QR for each unless provided
                // If platform provides ONE QR for multiple tickets, we might need logic here.
                // Assuming platform provides distinct QR per ticket or we handle it upstream.
                // For now, if qrCode is provided and quantity > 1, this will fail unique constraint.
                // Realistically, platform integrations usually sync individual tickets.
                qrCode = if (qrCode != null && quantity == 1) qrCode else qrCodeService.generateTicketQrCode(),
                ticketType = type,
                seatId = seatId,
                gaAreaId = gaAreaId,
                tableId = tableId,
                maxScanCount = maxScanCount
            )
        }

        return ticketRepository.saveAll(tickets).map { it.toDto() }
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

    private fun Ticket.toDto() = TicketDto(
        id = id,
        ticketNumber = null,
        qrCode = qrCode,
        ticketType = ticketType.name,
        status = status.name,
        maxScanCount = maxScanCount,
        scanCount = getScanCount(),
        remainingScans = getRemainingScans()
    )
}
