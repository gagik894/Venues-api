package app.venues.shared.pdf

import app.venues.shared.email.EmailTicket
import com.lowagie.text.*
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * Service for generating PDF tickets with QR codes.
 * Creates printable tickets that can be attached to confirmation emails.
 */
@Service
class PdfTicketService(
    private val messageSource: MessageSource
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Generate a PDF containing all tickets for a booking.
     *
     * @param eventTitle The event name
     * @param eventDate The event date
     * @param eventTime The event time
     * @param venueName The venue name
     * @param bookingReference The booking reference code
     * @param tickets List of tickets with QR codes
     * @param locale The locale for internationalization
     * @return PDF as byte array
     */
    fun generateTicketsPdf(
        eventTitle: String,
        eventDate: String,
        eventTime: String,
        venueName: String,
        bookingReference: String,
        tickets: List<EmailTicket>,
        locale: Locale = Locale.ENGLISH
    ): ByteArray {
        logger.debug { "Generating PDF for ${tickets.size} tickets, booking: $bookingReference" }

        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 40f, 40f, 50f, 50f)

        try {
            PdfWriter.getInstance(document, outputStream)
            document.open()

            tickets.forEachIndexed { index, ticket ->
                if (index > 0) {
                    document.newPage()
                }
                addTicketPage(document, eventTitle, eventDate, eventTime, venueName, bookingReference, ticket, locale)
            }

            document.close()
            logger.info { "Generated PDF with ${tickets.size} tickets for booking $bookingReference" }
            return outputStream.toByteArray()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate PDF for booking $bookingReference" }
            throw e
        }
    }

    /**
     * Generate a compact strip-style PDF for thermal printers.
     * Uses narrow page width; each ticket is a short page to save paper.
     */
    fun generateTicketsStripPdf(
        eventTitle: String,
        eventDate: String,
        eventTime: String,
        venueName: String,
        bookingReference: String,
        tickets: List<EmailTicket>,
        locale: Locale = Locale.ENGLISH
    ): ByteArray {
        logger.debug { "Generating strip PDF for ${tickets.size} tickets, booking: $bookingReference" }

        val outputStream = ByteArrayOutputStream()
        val pageSize = Rectangle(226.77f, 500f) // ~80mm width, adjustable height
        val document = Document(pageSize, 16f, 16f, 20f, 20f)

        try {
            PdfWriter.getInstance(document, outputStream)
            document.open()

            tickets.forEachIndexed { index, ticket ->
                if (index > 0) {
                    document.newPage()
                }
                addStripTicketPage(
                    document = document,
                    eventTitle = eventTitle,
                    eventDate = eventDate,
                    eventTime = eventTime,
                    venueName = venueName,
                    bookingReference = bookingReference,
                    ticket = ticket,
                    locale = locale,
                    total = tickets.size,
                    position = index + 1
                )
            }

            document.close()
            logger.info { "Generated strip PDF with ${tickets.size} tickets for booking $bookingReference" }
            return outputStream.toByteArray()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate strip PDF for booking $bookingReference" }
            throw e
        }
    }

    private fun addTicketPage(
        document: Document,
        eventTitle: String,
        eventDate: String,
        eventTime: String,
        venueName: String,
        bookingReference: String,
        ticket: EmailTicket,
        locale: Locale
    ) {
        // Fonts
        val titleFont = Font(Font.HELVETICA, 24f, Font.BOLD, Color.BLACK)
        val headerFont = Font(Font.HELVETICA, 14f, Font.BOLD, Color.DARK_GRAY)
        val normalFont = Font(Font.HELVETICA, 12f, Font.NORMAL, Color.BLACK)
        val smallFont = Font(Font.HELVETICA, 10f, Font.NORMAL, Color.GRAY)
        val ticketTypeFont = Font(Font.HELVETICA, 16f, Font.BOLD, Color.BLACK)

        // Title
        val title = Paragraph("🎫 ${getMessage("pdf.ticket.title", locale)}", titleFont)
        title.alignment = Element.ALIGN_CENTER
        title.spacingAfter = 20f
        document.add(title)

        // Event info table
        val infoTable = PdfPTable(2)
        infoTable.widthPercentage = 100f
        infoTable.setWidths(floatArrayOf(1f, 2f))
        document.add(infoTable)

        // Add spacing after table
        document.add(Chunk.NEWLINE)

        addInfoRow(infoTable, getMessage("pdf.ticket.event", locale), eventTitle, headerFont, normalFont)
        addInfoRow(infoTable, getMessage("pdf.ticket.date", locale), eventDate, headerFont, normalFont)
        addInfoRow(infoTable, getMessage("pdf.ticket.time", locale), eventTime, headerFont, normalFont)
        addInfoRow(infoTable, getMessage("pdf.ticket.venue", locale), venueName, headerFont, normalFont)
        addInfoRow(infoTable, getMessage("pdf.ticket.reference", locale), bookingReference, headerFont, normalFont)

        document.add(infoTable)

        // Divider
        val divider = Paragraph("─".repeat(60))
        divider.alignment = Element.ALIGN_CENTER
        divider.spacingAfter = 20f
        document.add(divider)

        // Seat/Location info (prominent multi-line display)
        // Each line displays a hierarchy level, ending with the specific seat/table/GA
        if (ticket.seatInfoLines.isNotEmpty()) {
            val locationFont = Font(Font.HELVETICA, 14f, Font.BOLD, Color.DARK_GRAY)
            ticket.seatInfoLines.forEach { line ->
                val linePara = Paragraph(line, locationFont)
                linePara.alignment = Element.ALIGN_CENTER
                linePara.spacingAfter = 4f
                document.add(linePara)
            }
            document.add(Chunk.NEWLINE)
        } else {
            // Fallback: show ticket type label if no location info
            val ticketTypeLabel = getTicketTypeLabel(ticket.ticketType, locale)
            val ticketTypePara = Paragraph(ticketTypeLabel, headerFont)
            ticketTypePara.alignment = Element.ALIGN_CENTER
            ticketTypePara.spacingAfter = 15f
            document.add(ticketTypePara)
        }

        // Ticket number
        val ticketNumPara = Paragraph(ticket.ticketNumber, smallFont)
        ticketNumPara.alignment = Element.ALIGN_CENTER
        ticketNumPara.spacingAfter = 20f
        document.add(ticketNumPara)

        // QR Code
        try {
            val qrImageBytes = Base64.getDecoder().decode(ticket.qrCodeBase64)
            val qrImage = Image.getInstance(qrImageBytes)
            qrImage.scaleToFit(200f, 200f)
            qrImage.alignment = Element.ALIGN_CENTER
            document.add(qrImage)
        } catch (e: Exception) {
            logger.warn { "Could not add QR code to PDF: ${e.message}" }
            val errorPara = Paragraph("[QR Code]", normalFont)
            errorPara.alignment = Element.ALIGN_CENTER
            document.add(errorPara)
        }

        // Scan instruction
        val scanPara = Paragraph(getMessage("pdf.ticket.scan.instruction", locale), smallFont)
        scanPara.alignment = Element.ALIGN_CENTER
        scanPara.spacingBefore = 10f
        document.add(scanPara)

        // Footer
        val footer = Paragraph(getMessage("pdf.ticket.footer", locale), smallFont)
        footer.alignment = Element.ALIGN_CENTER
        footer.spacingBefore = 30f
        document.add(footer)
    }

    private fun addStripTicketPage(
        document: Document,
        eventTitle: String,
        eventDate: String,
        eventTime: String,
        venueName: String,
        bookingReference: String,
        ticket: EmailTicket,
        locale: Locale,
        total: Int,
        position: Int
    ) {
        val titleFont = Font(Font.HELVETICA, 14f, Font.BOLD, Color.BLACK)
        val headerFont = Font(Font.HELVETICA, 11f, Font.BOLD, Color.DARK_GRAY)
        val normalFont = Font(Font.HELVETICA, 10f, Font.NORMAL, Color.BLACK)
        val smallFont = Font(Font.HELVETICA, 9f, Font.NORMAL, Color.GRAY)

        val title = Paragraph(getMessage("pdf.ticket.title", locale), titleFont)
        title.alignment = Element.ALIGN_CENTER
        title.spacingAfter = 6f
        document.add(title)

        val infoTable = PdfPTable(2)
        infoTable.widthPercentage = 100f
        infoTable.setWidths(floatArrayOf(1f, 1.4f))

        addInfoRow(infoTable, getMessage("pdf.ticket.event", locale), eventTitle, headerFont, normalFont)
        addInfoRow(infoTable, getMessage("pdf.ticket.date", locale), eventDate, headerFont, normalFont)
        addInfoRow(infoTable, getMessage("pdf.ticket.time", locale), eventTime, headerFont, normalFont)
        addInfoRow(infoTable, getMessage("pdf.ticket.venue", locale), venueName, headerFont, normalFont)
        addInfoRow(infoTable, getMessage("pdf.ticket.reference", locale), bookingReference, headerFont, normalFont)

        document.add(infoTable)

        val divider = Paragraph("-".repeat(40), smallFont)
        divider.alignment = Element.ALIGN_CENTER
        divider.spacingAfter = 6f
        document.add(divider)

        if (ticket.seatInfoLines.isNotEmpty()) {
            val locationFont = Font(Font.HELVETICA, 11f, Font.BOLD, Color.DARK_GRAY)
            ticket.seatInfoLines.forEach { line ->
                val linePara = Paragraph(line, locationFont)
                linePara.alignment = Element.ALIGN_CENTER
                linePara.spacingAfter = 2f
                document.add(linePara)
            }
            document.add(Chunk.NEWLINE)
        }

        val ticketNumPara = Paragraph("${position}/${total}", smallFont)
        ticketNumPara.alignment = Element.ALIGN_CENTER
        ticketNumPara.spacingAfter = 6f
        document.add(ticketNumPara)

        try {
            val qrImageBytes = Base64.getDecoder().decode(ticket.qrCodeBase64)
            val qrImage = Image.getInstance(qrImageBytes)
            qrImage.scaleToFit(140f, 140f)
            qrImage.alignment = Element.ALIGN_CENTER
            document.add(qrImage)
        } catch (e: Exception) {
            logger.warn { "Could not add QR code to strip PDF: ${e.message}" }
            val errorPara = Paragraph("[QR Code]", normalFont)
            errorPara.alignment = Element.ALIGN_CENTER
            document.add(errorPara)
        }

        val scanPara = Paragraph(getMessage("pdf.ticket.scan.instruction", locale), smallFont)
        scanPara.alignment = Element.ALIGN_CENTER
        scanPara.spacingBefore = 6f
        document.add(scanPara)
    }

    private fun addInfoRow(table: PdfPTable, label: String, value: String, labelFont: Font, valueFont: Font) {
        val labelCell = PdfPCell(Phrase(label, labelFont))
        labelCell.border = Rectangle.NO_BORDER
        labelCell.paddingBottom = 8f
        table.addCell(labelCell)

        val valueCell = PdfPCell(Phrase(value, valueFont))
        valueCell.border = Rectangle.NO_BORDER
        valueCell.paddingBottom = 8f
        table.addCell(valueCell)
    }

    private fun getTicketTypeLabel(ticketType: String, locale: Locale): String {
        return getMessage("email.booking.ticket.type.$ticketType", locale)
    }

    private fun getMessage(key: String, locale: Locale): String {
        return try {
            messageSource.getMessage(key, null, key, locale) ?: key
        } catch (e: Exception) {
            key
        }
    }
}
