package app.venues.ticket.service

import app.venues.booking.api.BookingApi
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.ticket.api.TicketScanApi
import app.venues.ticket.api.dto.ScanResult
import app.venues.ticket.api.dto.TicketScanInfoDto
import app.venues.ticket.domain.TicketStatus
import app.venues.ticket.domain.TicketType
import app.venues.ticket.repository.TicketRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class TicketScanService(
    private val ticketRepository: TicketRepository,
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi,
    private val bookingApi: BookingApi
) : TicketScanApi {

    @Transactional
    override fun scanTicket(
        qrCode: String,
        sessionId: UUID,
        deviceInfo: String?,
        scanLocation: String?
    ): ScanResult {
        // 1. Find ticket
        val ticket = ticketRepository.findByQrCode(qrCode)
        if (ticket == null) {
            return ScanResult.notFound()
        }

        // 2. Validate status
        if (ticket.status == TicketStatus.INVALIDATED) {
            return ScanResult.error("Ticket invalidated: ${ticket.invalidationReason}")
        }
        if (ticket.status == TicketStatus.EXPIRED) {
            return ScanResult.error("Ticket expired")
        }

        // 3. Check scan count
        if (!ticket.canBeScanned()) {
            return ScanResult.alreadyScanned(ticket.getScanCount(), ticket.maxScanCount)
        }

        // 4. Perform scan
        val scan = ticket.scan(sessionId)
        scan.deviceInfo = deviceInfo
        scan.scanLocation = scanLocation
        ticketRepository.save(ticket)

        // 5. Fetch info for display
        val eventSession = eventApi.getEventSessionInfo(ticket.eventSessionId)
        val booking = try {
            bookingApi.getBookingById(ticket.bookingId)
        } catch (e: Exception) {
            null // Handle case where booking might not be found or other error
        }

        val seatInfo = when (ticket.ticketType) {
            TicketType.SEAT -> ticket.seatId?.let { seatingApi.getSeatInfo(it)?.code }
            TicketType.TABLE -> ticket.tableId?.let { seatingApi.getTableInfo(it)?.code }
            TicketType.GA -> ticket.gaAreaId?.let { seatingApi.getGaInfo(it)?.name }
        }

        val info = TicketScanInfoDto(
            eventTitle = eventSession?.eventTitle ?: "Unknown Event",
            customerName = booking?.customerName,
            seatInfo = seatInfo,
            ticketType = ticket.ticketType.name,
            scanCount = ticket.getScanCount(),
            maxScans = ticket.maxScanCount
        )

        return ScanResult.success(info, scan.scannedAt)
    }
}
