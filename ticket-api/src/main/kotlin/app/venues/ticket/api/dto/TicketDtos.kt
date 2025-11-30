package app.venues.ticket.api.dto

import java.util.*

data class TicketDto(
    val id: UUID,
    val ticketNumber: String?, // Kept optional in DTO if needed for display, though removed from entity
    val qrCode: String,
    val ticketType: String,
    val status: String,
    val maxScanCount: Int,
    val scanCount: Int,
    val remainingScans: Int
)


