package app.venues.ticket.api.mapper

import app.venues.ticket.api.dto.TicketDto
import app.venues.ticket.api.dto.TicketResponse
import app.venues.ticket.domain.Ticket
import org.springframework.stereotype.Component

@Component
class TicketMapper {

    fun toDto(ticket: Ticket): TicketDto {
        return TicketDto(
            id = ticket.id,
            qrCode = ticket.qrCode,
            ticketType = ticket.ticketType.name,
            status = ticket.status.name,
            maxScanCount = ticket.maxScanCount,
            scanCount = ticket.getScanCount(),
            remainingScans = ticket.getRemainingScans(),
            seatId = ticket.seatId,
            gaAreaId = ticket.gaAreaId,
            tableId = ticket.tableId
        )
    }

    fun toResponse(ticket: Ticket): TicketResponse {
        return TicketResponse(
            id = ticket.id,
            qrCode = ticket.qrCode,
            ticketType = ticket.ticketType.name,
            status = ticket.status.name,
            maxScanCount = ticket.maxScanCount,
            scanCount = ticket.getScanCount(),
            remainingScans = ticket.getRemainingScans()
        )
    }
}
