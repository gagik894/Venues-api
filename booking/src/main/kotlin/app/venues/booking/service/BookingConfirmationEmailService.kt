package app.venues.booking.service

import app.venues.booking.domain.Booking
import app.venues.booking.repository.BookingRepository
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.shared.email.*
import app.venues.shared.pdf.PdfTicketService
import app.venues.shared.qrcode.QRCodeService
import app.venues.ticket.api.TicketApi
import app.venues.ticket.api.dto.TicketDto
import app.venues.user.api.UserApi
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
 * - Attaching PDF tickets when there are many tickets (>3)
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
    private val emailTemplateService: EmailTemplateService,
    private val pdfTicketService: PdfTicketService
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        const val PDF_THRESHOLD = 3 // Use PDF attachment if more than 3 tickets
    }

    /**
     * Send booking confirmation email with tickets and QR codes.
     *
     * @param booking The confirmed booking entity
     * @param locale The locale for email internationalization (e.g., "en", "hy", "ru")
     */
    fun sendConfirmationEmail(booking: Booking, locale: String? = null) {
        try {
            doSendConfirmationEmail(booking, locale)
        } catch (e: Exception) {
            logger.error(e) { "Failed to send confirmation email for booking ${booking.id}" }
        }
    }

    /**
     * Send booking confirmation email by booking ID.
     * Fetches booking from repository within a transaction to ensure lazy loading works.
     *
     * @param bookingId The booking UUID
     * @param locale The locale for email internationalization (e.g., "en", "hy", "ru")
     */
    @Transactional(readOnly = true)
    fun sendConfirmationEmail(bookingId: UUID, locale: String? = null) {
        val booking = bookingRepository.findById(bookingId).orElse(null)
        if (booking == null) {
            logger.warn { "Booking not found for email: $bookingId" }
            return
        }
        sendConfirmationEmail(booking, locale)
    }

    private fun doSendConfirmationEmail(booking: Booking, localeCode: String?) {
        // Resolve locale for i18n
        val locale = resolveLocale(localeCode)
        val languageCode = locale.language // e.g., "en", "hy", "ru"

        // Fetch session details
        val sessionDto = eventApi.getEventSessionInfo(booking.sessionId)
        if (sessionDto == null) {
            logger.warn { "Session not found for booking ${booking.id}, skipping email" }
            return
        }

        // Get translated venue and event names
        val venueId = booking.venueId
        val venueName = venueId?.let {
            venueApi.getVenueNameTranslated(it, languageCode)
        } ?: "Venues App"

        val eventTitle = eventApi.getEventTitleTranslated(sessionDto.eventId, languageCode)
            ?: sessionDto.eventTitle

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

        // Always use PDF attachment (no inline tickets)
        val attachments = mutableListOf<EmailAttachment>()

        // Generate email content with i18n
        val zoneId = ZoneId.systemDefault()

        // Generate PDF and attach it
        val pdfBytes = pdfTicketService.generateTicketsPdf(
            eventTitle = eventTitle,
            eventDate = sessionDto.startTime.atZone(zoneId).toLocalDate().toString(),
            eventTime = sessionDto.startTime.atZone(zoneId).toLocalTime().toString(),
            venueName = venueName,
            bookingReference = booking.externalOrderNumber ?: booking.id.toString().take(8).uppercase(),
            tickets = emailTickets,
            locale = locale
        )
        attachments.add(
            EmailAttachment(
                filename = "tickets-${booking.externalOrderNumber ?: booking.id.toString().take(8)}.pdf",
                contentType = "application/pdf",
                data = pdfBytes
            )
        )

        // Email content without inline tickets (PDF attached)
        val content = emailTemplateService.generateBookingConfirmationEmail(
            name = customerName,
            bookingReference = booking.externalOrderNumber
                ?: booking.id.toString().take(8).uppercase(),
            eventTitle = eventTitle,
            eventDate = sessionDto.startTime.atZone(zoneId).toLocalDate().toString(),
            eventTime = sessionDto.startTime.atZone(zoneId).toLocalTime().toString(),
            venueName = venueName,
            items = items,
            totalPrice = "${booking.currency} ${booking.totalPrice}",
            locale = locale
        )

        // Send email
        sendEmail(venueId, customerEmail, venueName, content, attachments)

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

    /**
     * Resolve Locale from language code string.
     * Supports "en", "hy", "ru" with English as default.
     */
    private fun resolveLocale(localeCode: String?): Locale {
        return when (localeCode?.lowercase()) {
            "hy" -> Locale("hy")
            "ru" -> Locale("ru")
            else -> Locale.ENGLISH
        }
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
                ticketType = ticket.ticketType,  // Pass raw type for i18n (SEAT, GA, TABLE)
                seatInfo = seatInfo,
                ticketNumber = "Ticket ${index + 1} of ${tickets.size}"
            )
        }
    }

    /**
     * Resolve human-readable seat/GA/table info for ticket display.
     * Builds full hierarchy path like "Right Tribune > Sector 5 > Row A > Seat 10"
     */
    private fun resolveSeatInfo(ticket: TicketDto): String? {
        val seatId = ticket.seatId
        val gaAreaId = ticket.gaAreaId
        val tableId = ticket.tableId

        return when {
            seatId != null -> resolveSeatPath(seatId)
            gaAreaId != null -> resolveGaPath(gaAreaId)
            tableId != null -> resolveTablePath(tableId)
            else -> null
        }
    }

    /**
     * Build full seat path: "Zone1 > Zone2 > Row X > Seat Y"
     */
    private fun resolveSeatPath(seatId: Long): String? {
        return try {
            val seatInfo = seatingApi.getSeatInfo(seatId) ?: return null
            val hierarchy = seatingApi.getZoneHierarchy(seatInfo.zoneId)

            val pathParts = mutableListOf<String>()
            // Add zone hierarchy (from root to leaf)
            hierarchy.forEach { zone -> pathParts.add(zone.name) }
            // Add row and seat
            if (seatInfo.rowLabel.isNotBlank()) {
                pathParts.add("Row ${seatInfo.rowLabel}")
            }
            pathParts.add("Seat ${seatInfo.seatNumber}")

            pathParts.joinToString(" › ")
        } catch (e: Exception) {
            logger.debug { "Could not resolve seat path for $seatId: ${e.message}" }
            null
        }
    }

    /**
     * Build GA area path: "Zone1 > Zone2 > GA Area Name"
     */
    private fun resolveGaPath(gaAreaId: Long): String? {
        return try {
            val gaInfo = seatingApi.getGaInfo(gaAreaId) ?: return null
            val hierarchy = seatingApi.getZoneHierarchy(gaInfo.zoneId)

            val pathParts = mutableListOf<String>()
            hierarchy.forEach { zone -> pathParts.add(zone.name) }
            pathParts.add(gaInfo.name)

            pathParts.joinToString(" › ")
        } catch (e: Exception) {
            logger.debug { "Could not resolve GA path for $gaAreaId: ${e.message}" }
            null
        }
    }

    /**
     * Build table path: "Zone1 > Zone2 > Table X"
     */
    private fun resolveTablePath(tableId: Long): String? {
        return try {
            val tableInfo = seatingApi.getTableInfo(tableId) ?: return null
            val hierarchy = seatingApi.getZoneHierarchy(tableInfo.zoneId)

            val pathParts = mutableListOf<String>()
            hierarchy.forEach { zone -> pathParts.add(zone.name) }
            pathParts.add("Table ${tableInfo.tableNumber}")

            pathParts.joinToString(" › ")
        } catch (e: Exception) {
            logger.debug { "Could not resolve table path for $tableId: ${e.message}" }
            null
        }
    }

    private fun sendEmail(
        venueId: UUID?,
        customerEmail: String,
        venueName: String,
        content: String,
        attachments: List<EmailAttachment> = emptyList()
    ) {
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
            emailService.sendVenueEmailWithAttachments(
                config = emailConfig,
                to = customerEmail,
                subject = subject,
                content = content,
                isHtml = true,
                attachments = attachments
            )
        } else {
            emailService.sendGlobalEmailWithAttachments(
                to = customerEmail,
                subject = subject,
                content = content,
                isHtml = true,
                attachments = attachments
            )
        }
    }
}
