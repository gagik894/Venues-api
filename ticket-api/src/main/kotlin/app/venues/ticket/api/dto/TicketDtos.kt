package app.venues.ticket.api.dto

import java.util.*

data class TicketDto(
    val id: UUID,
    val qrCode: String,
    val ticketType: String,
    val status: String,
    val maxScanCount: Int,
    val scanCount: Int,
    val remainingScans: Int,
    val seatId: Long? = null,
    val gaAreaId: Long? = null,
    val tableId: Long? = null
)