package app.venues.ticket.api.dto

import java.time.Instant
import java.util.*

data class ScanResult(
    val success: Boolean,
    val message: String,
    val ticketInfo: TicketScanInfoDto? = null,
    val scanTimestamp: Instant? = null,
    val lastScanTimestamp: Instant? = null
) {
    companion object {
        fun notFound() = ScanResult(false, "Ticket not found")
        fun invalidSession() = ScanResult(false, "Invalid scanner session")
        fun alreadyScanned(scanCount: Int, maxScans: Int, lastScan: Instant?) =
            ScanResult(false, "Ticket already scanned ($scanCount/$maxScans)", lastScanTimestamp = lastScan)

        fun success(ticketInfo: TicketScanInfoDto, timestamp: Instant, lastScan: Instant?) =
            ScanResult(true, "Entry granted", ticketInfo, timestamp, lastScan)

        fun error(message: String) = ScanResult(false, message)
    }
}

data class TicketScanInfoDto(
    val eventTitle: String?,
    val customerName: String?,
    val seatDetail: TicketSeatDetailDto?,
    val ticketType: String,
    val scanCount: Int,
    val maxScans: Int
)

data class TicketSeatDetailDto(
    val sectionNames: List<String>,
    val rowLabel: String?,
    val seatNumber: String?
)

data class ScannerSessionDto(
    val id: UUID,
    val sessionName: String,
    val secretToken: String,
    val qrCodeData: String,
    val validUntil: Instant,
    val active: Boolean
)

data class CreateSessionRequest(
    val eventId: UUID,
    val sessionName: String,
    val validUntil: Instant,
    val scanLocation: String?,
    val venueId: UUID
)

data class ScanRequest(
    val qrCode: String,
    val deviceInfo: String?,
    val scanLocation: String?
)

data class TicketResponse(
    val id: UUID,
    val qrCode: String,
    val ticketType: String,
    val status: String,
    val maxScanCount: Int,
    val scanCount: Int,
    val remainingScans: Int
)
