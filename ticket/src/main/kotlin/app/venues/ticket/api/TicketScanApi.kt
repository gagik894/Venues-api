package app.venues.ticket.api

import app.venues.ticket.api.dto.ScanResult
import java.util.*

interface TicketScanApi {
    fun scanTicket(
        qrCode: String,
        sessionId: UUID,
        deviceInfo: String?,
        scanLocation: String?
    ): ScanResult
}
