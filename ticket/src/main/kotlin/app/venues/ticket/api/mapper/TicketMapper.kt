package app.venues.ticket.api.mapper

import app.venues.ticket.api.dto.TicketDto
import app.venues.ticket.domain.Ticket
import org.springframework.stereotype.Component

@Component
class TicketMapper {

    fun toDto(ticket: Ticket): TicketDto {
        return TicketDto(
            id = ticket.id,
            ticketNumber = null, // Not used in entity
            qrCode = ticket.qrCode,
            ticketType = ticket.ticketType.name,
            status = ticket.status.name,
            maxScanCount = ticket.maxScanCount,
            scanCount = ticket.getScanCount(),
            remainingScans = ticket.getRemainingScans()
        )
    }
}
