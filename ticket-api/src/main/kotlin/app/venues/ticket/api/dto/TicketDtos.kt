package app.venues.ticket.api.dto

import java.time.Instant
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

data class ScanResult(
    val success: Boolean,
    val message: String,
    val ticketInfo: TicketScanInfoDto? = null,
    val scanTimestamp: Instant? = null
) {
    companion object {
        fun notFound() = ScanResult(false, "Ticket not found")
        fun invalidSession() = ScanResult(false, "Invalid scanner session")
        fun alreadyScanned(scanCount: Int, maxScans: Int) =
            ScanResult(false, "Ticket already scanned ($scanCount/$maxScans)")

        fun success(ticketInfo: TicketScanInfoDto, timestamp: Instant) =
            ScanResult(true, "Entry granted", ticketInfo, timestamp)

        fun error(message: String) = ScanResult(false, message)
    }
}

data class TicketScanInfoDto(
    val eventTitle: String?,
    val customerName: String?,
    val seatInfo: String?,
    val ticketType: String,
    val scanCount: Int,
    val maxScans: Int
)

data class ScannerSessionDto(
    val id: UUID,
    val sessionName: String,
    val secretToken: String,
    val qrCodeData: String,
    val qrCodeImage: String?, // Base64 image
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
