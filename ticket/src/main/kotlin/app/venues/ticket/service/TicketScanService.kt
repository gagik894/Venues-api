package app.venues.ticket.service

import app.venues.booking.api.BookingApi
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.ticket.api.TicketScanApi
import app.venues.ticket.api.dto.ScanResult
import app.venues.ticket.api.dto.TicketScanInfoDto
import app.venues.ticket.api.dto.TicketSeatDetailDto
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
    private val bookingApi: BookingApi,
    private val scannerSessionRepository: app.venues.ticket.repository.ScannerSessionRepository
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

        // 2. Validate scanner session
        val scannerSession = scannerSessionRepository.findById(sessionId)
            .orElse(null) ?: return ScanResult.invalidSession()

        if (!scannerSession.isValid()) {
            return ScanResult.invalidSession()
        }

        // 3. Validate event match
        val eventSession = eventApi.getEventSessionInfo(ticket.eventSessionId)
            ?: return ScanResult.error("Event session not found")

        if (eventSession.eventId != scannerSession.eventId) {
            return ScanResult.error("Ticket belongs to a different event")
        }

        // 4. Validate status
        if (ticket.status == TicketStatus.INVALIDATED) {
            return ScanResult.error("Ticket invalidated: ${ticket.invalidationReason}")
        }
        if (ticket.status == TicketStatus.EXPIRED) {
            return ScanResult.error("Ticket expired")
        }

        // 5. Check scan count
        if (!ticket.canBeScanned()) {
            return ScanResult.alreadyScanned(ticket.getScanCount(), ticket.maxScanCount, ticket.getLastScanAt())
        }

        // 6. Perform scan
        val scan = ticket.scan(sessionId)
        scan.deviceInfo = deviceInfo
        scan.scanLocation = scanLocation
        ticketRepository.save(ticket)

        // 7. Fetch info for display (booking already fetched if needed, but we do it lazily or here)
        val booking = try {
            bookingApi.getBookingById(ticket.bookingId)
        } catch (e: Exception) {
            null // Handle case where booking might not be found or other error
        }

        val seatDetail = when (ticket.ticketType) {
            TicketType.SEAT -> ticket.seatId?.let { seatId ->
                val seat = seatingApi.getSeatInfo(seatId)
                seat?.let {
                    val hierarchy = seatingApi.getZoneHierarchy(it.zoneId)
                    TicketSeatDetailDto(
                        sectionNames = hierarchy.map { zone -> zone.name },
                        rowLabel = it.rowLabel,
                        seatNumber = it.seatNumber
                    )
                }
            }

            TicketType.TABLE -> ticket.tableId?.let { tableId ->
                val table = seatingApi.getTableInfo(tableId)
                table?.let {
                    val hierarchy = seatingApi.getZoneHierarchy(it.zoneId)
                    TicketSeatDetailDto(
                        sectionNames = hierarchy.map { zone -> zone.name },
                        rowLabel = "Table",
                        seatNumber = it.tableNumber
                    )
                }
            }

            TicketType.GA -> ticket.gaAreaId?.let { gaId ->
                val ga = seatingApi.getGaInfo(gaId)
                ga?.let {
                    val hierarchy = seatingApi.getZoneHierarchy(it.zoneId)
                    TicketSeatDetailDto(
                        sectionNames = hierarchy.map { zone -> zone.name } + it.name,
                        rowLabel = null,
                        seatNumber = null
                    )
                }
            }
        }

        val info = TicketScanInfoDto(
            eventTitle = eventSession?.eventTitle ?: "Unknown Event",
            customerName = booking?.customerName,
            seatDetail = seatDetail,
            ticketType = ticket.ticketType.name,
            scanCount = ticket.getScanCount(),
            maxScans = ticket.maxScanCount
        )

        val scanTime = scan.scannedAt
        return ScanResult.success(info, scanTime, scanTime)
    }
}
