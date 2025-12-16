package app.venues.booking.service

import app.venues.booking.domain.Booking
import app.venues.booking.repository.BookingRepository
import app.venues.booking.service.GuestService.Companion.isPlaceholderEmail
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.shared.email.*
import app.venues.shared.money.toMoney
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
                price = item.unitPrice.toMoney(booking.currency)
            )
        }

        // Get tickets and generate QR codes with seat info
        val emailTickets = generateEmailTickets(booking.id, locale)

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
            totalPrice = booking.totalPrice.toMoney(booking.currency),
            locale = locale
        )

        // Send email
        sendEmail(venueId, customerEmail, venueName, content, attachments)

        logger.info { "Confirmation email sent for booking ${booking.id} to $customerEmail" }
    }

    private fun getCustomerEmail(booking: Booking): String? {
        val email = booking.userId?.let { userApi.getUserEmail(it) }
            ?: booking.guest?.email
        val trimmed = email?.trim()
        if (trimmed.isNullOrBlank() || isPlaceholderEmail(trimmed)) {
            return null
        }
        return trimmed
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

    private fun generateEmailTickets(bookingId: UUID, locale: Locale): List<EmailTicket> {
        val tickets = ticketApi.getTicketsForBooking(bookingId)
        if (tickets.isEmpty()) {
            logger.debug { "No tickets found for booking $bookingId" }
            return emptyList()
        }

        return tickets.mapIndexed { index, ticket ->
            val qrCodeBase64 = qrCodeService.generateQrCodeImageBase64(ticket.qrCode)
            val seatInfoLines = resolveSeatInfoLines(ticket, locale)

            EmailTicket(
                qrCodeBase64 = qrCodeBase64,
                ticketType = ticket.ticketType,  // Pass raw type for i18n (SEAT, GA, TABLE)
                seatInfoLines = seatInfoLines,
                ticketNumber = "Ticket ${index + 1} of ${tickets.size}"
            )
        }
    }

    /**
     * Resolve human-readable seat/GA/table info for ticket display.
     * Delegates to SeatingApi for location line resolution.
     * Returns a list of lines for multi-line display in PDF.
     * Example for seat: ["Right Tribune", "Sector 5", "Row 3", "Seat 10"]
     * Example for GA: ["Fan Zone"]
     * Example for table: ["VIP Section", "Table 5"]
     */
    private fun resolveSeatInfoLines(ticket: TicketDto, locale: Locale): List<String> {
        val languageCode = locale.language
        val seatId = ticket.seatId
        val gaAreaId = ticket.gaAreaId
        val tableId = ticket.tableId

        return when {
            seatId != null -> seatingApi.getSeatLocationLines(seatId, languageCode)
            gaAreaId != null -> seatingApi.getGaLocationLines(gaAreaId, languageCode)
            tableId != null -> seatingApi.getTableLocationLines(tableId, languageCode)
            else -> emptyList()
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
