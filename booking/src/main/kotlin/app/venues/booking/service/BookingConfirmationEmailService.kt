package app.venues.booking.service

import app.venues.booking.domain.Booking
import app.venues.booking.repository.BookingRepository
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.shared.email.EmailBookingItem
import app.venues.shared.email.EmailConfig
import app.venues.shared.email.EmailService
import app.venues.shared.email.EmailTemplateService
import app.venues.shared.email.EmailTicket
import app.venues.shared.qrcode.QRCodeService
import app.venues.ticket.api.TicketApi
import app.venues.ticket.api.dto.TicketDto
import app.venues.user.api.UserApi
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.util.*

/**
 * Service for sending booking confirmation emails with tickets.
 *
 * Single source of truth for email generation and sending.
 * Handles:
 * - Fetching booking and related data
 * - Generating QR codes for tickets
 * - Resolving seat/GA/table info for ticket display
 * - Composing and sending emails via venue or global SMTP
 */
@Service
class BookingConfirmationEmailService(
    private val bookingRepository: BookingRepository,
    private val ticketApi: TicketApi,
    private val qrCodeService: QRCodeService,
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi,
    private val userApi: UserApi,
    private val venueApi: VenueApi,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Send booking confirmation email with tickets and QR codes.
     *
     * @param booking The confirmed booking entity
     */
    fun sendConfirmationEmail(booking: Booking) {
        try {
            doSendConfirmationEmail(booking)
        } catch (e: Exception) {
            logger.error(e) { "Failed to send confirmation email for booking ${booking.id}" }
        }
    }

    /**
     * Send booking confirmation email by booking ID.
     * Fetches booking from repository.
     *
     * @param bookingId The booking UUID
     */
    fun sendConfirmationEmail(bookingId: UUID) {
        val booking = bookingRepository.findById(bookingId).orElse(null)
        if (booking == null) {
            logger.warn { "Booking not found for email: $bookingId" }
            return
        }
        sendConfirmationEmail(booking)
    }

    private fun doSendConfirmationEmail(booking: Booking) {
        // Fetch session details
        val sessionDto = eventApi.getEventSessionInfo(booking.sessionId)
        if (sessionDto == null) {
            logger.warn { "Session not found for booking ${booking.id}, skipping email" }
            return
        }

        val venueId = booking.venueId
        val venueName = venueId?.let { venueApi.getVenueName(it) } ?: "Venues App"

        // Get customer info
        val customerEmail = getCustomerEmail(booking)
        if (customerEmail == null) {
            logger.warn { "No customer email for booking ${booking.id}, skipping email" }
            return
        }
        val customerName = getCustomerName(booking)

        // Prepare booking items summary
        val items = booking.items.map { item ->
            EmailBookingItem(
                description = item.priceTemplateName ?: "Ticket",
                quantity = item.quantity,
                price = "${booking.currency} ${item.unitPrice}"
            )
        }

        // Get tickets and generate QR codes with seat info
        val emailTickets = generateEmailTickets(booking.id)

        // Generate email content
        val zoneId = ZoneId.systemDefault()
        val content = emailTemplateService.generateBookingConfirmationEmail(
            name = customerName,
            bookingReference = booking.externalOrderNumber
                ?: booking.id.toString().take(8).uppercase(),
            bookingUrl = "https://venues.app/bookings/${booking.id}",
            eventTitle = sessionDto.eventTitle,
            eventDate = sessionDto.startTime.atZone(zoneId).toLocalDate().toString(),
            eventTime = sessionDto.startTime.atZone(zoneId).toLocalTime().toString(),
            venueName = venueName,
            items = items,
            totalPrice = "${booking.currency} ${booking.totalPrice}",
            tickets = emailTickets
        )

        // Send email
        sendEmail(venueId, customerEmail, venueName, content)

        logger.info { "Confirmation email sent for booking ${booking.id} to $customerEmail" }
    }

    private fun getCustomerEmail(booking: Booking): String? {
        return booking.userId?.let { userApi.getUserEmail(it) }
            ?: booking.guest?.email
    }

    private fun getCustomerName(booking: Booking): String {
        return booking.userId?.let { userApi.getUserFullName(it) }
            ?: booking.guest?.name
            ?: "Customer"
    }

    private fun generateEmailTickets(bookingId: UUID): List<EmailTicket> {
        val tickets = ticketApi.getTicketsForBooking(bookingId)
        if (tickets.isEmpty()) {
            logger.debug { "No tickets found for booking $bookingId" }
            return emptyList()
        }

        return tickets.mapIndexed { index, ticket ->
            val qrCodeBase64 = qrCodeService.generateQrCodeImageBase64(ticket.qrCode)
            val seatInfo = resolveSeatInfo(ticket)

            EmailTicket(
                qrCodeBase64 = qrCodeBase64,
                ticketType = formatTicketType(ticket.ticketType),
                seatInfo = seatInfo,
                ticketNumber = "Ticket ${index + 1} of ${tickets.size}"
            )
        }
    }

    /**
     * Resolve human-readable seat/GA/table info for ticket display.
     */
    private fun resolveSeatInfo(ticket: TicketDto): String? {
        val seatId = ticket.seatId
        val gaAreaId = ticket.gaAreaId
        val tableId = ticket.tableId

        return when {
            seatId != null -> {
                try {
                    val seatInfo = seatingApi.getSeatInfo(seatId)
                    seatInfo?.let { "Seat: ${it.code}" }
                } catch (e: Exception) {
                    logger.debug { "Could not resolve seat info for $seatId" }
                    null
                }
            }
            gaAreaId != null -> {
                try {
                    val gaInfo = seatingApi.getGaInfo(gaAreaId)
                    gaInfo?.let { "General Admission: ${it.name}" }
                } catch (e: Exception) {
                    logger.debug { "Could not resolve GA info for $gaAreaId" }
                    null
                }
            }
            tableId != null -> {
                try {
                    val tableInfo = seatingApi.getTableInfo(tableId)
                    tableInfo?.let { "Table: ${it.tableNumber}" }
                } catch (e: Exception) {
                    logger.debug { "Could not resolve table info for $tableId" }
                    null
                }
            }
            else -> null
        }
    }

    private fun formatTicketType(ticketType: String): String {
        return when (ticketType) {
            "SEAT" -> "Reserved Seat"
            "GA" -> "General Admission"
            "TABLE" -> "Table Reservation"
            else -> ticketType
        }
    }

    private fun sendEmail(venueId: UUID?, customerEmail: String, venueName: String, content: String) {
        val subject = "Your Tickets - $venueName"

        val smtpDto = venueId?.let { venueApi.getSmtpConfig(it) }
        val emailConfig = smtpDto?.let {
            EmailConfig(
                host = it.host,
                port = it.port,
                username = it.email,
                password = it.password,
                startTls = it.tls
            )
        }

        if (emailConfig != null) {
            emailService.sendVenueEmail(
                config = emailConfig,
                to = customerEmail,
                subject = subject,
                content = content,
                isHtml = true
            )
        } else {
            emailService.sendGlobalEmail(
                to = customerEmail,
                subject = subject,
                content = content,
                isHtml = true
            )
        }
    }
}
