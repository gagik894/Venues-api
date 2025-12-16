package app.venues.booking.service

import app.venues.booking.api.StaffCartApi
import app.venues.booking.api.dto.StaffCartCheckoutRequest
import app.venues.booking.api.dto.StaffCartCheckoutResponse
import app.venues.booking.api.dto.TicketDeliveryInfo
import app.venues.booking.api.dto.TicketDeliveryPayload
import app.venues.booking.domain.SalesChannel
import app.venues.booking.event.BookingConfirmedEvent
import app.venues.booking.manager.CartSessionManager
import app.venues.booking.repository.BookingRepository
import app.venues.booking.repository.CartRepository
import app.venues.booking.service.model.BookingCreationContext
import app.venues.booking.service.model.CartSnapshot
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.shared.email.EmailTicket
import app.venues.shared.pdf.PdfTicketService
import app.venues.shared.qrcode.QRCodeService
import app.venues.ticket.api.TicketApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.util.*

/**
 * Service for staff cart operations.
 *
 * Handles conversion of staff carts to confirmed bookings for direct sales.
 * Staff carts are used when venue staff sell tickets in-person with immediate payment.
 *
 * Design:
 * - Reuses existing CartService for all cart manipulation (add/remove items)
 * - Uses BookingCreationService to assemble booking from cart (items already reserved)
 * - Immediately confirms and finalizes booking (payment assumed received)
 * - Cleans up cart after successful checkout
 */
@Service
@Transactional
class StaffCartService(
    private val cartSessionManager: CartSessionManager,
    private val cartRepository: CartRepository,
    private val bookingRepository: BookingRepository,
    private val bookingCreationService: BookingCreationService,
    private val bookingFulfillmentService: BookingFulfillmentService,
    private val bookingService: BookingService,
    private val guestService: GuestService,
    private val ticketApi: TicketApi,
    private val qrCodeService: QRCodeService,
    private val seatingApi: SeatingApi,
    private val pdfTicketService: PdfTicketService,
    private val venueApi: app.venues.venue.api.VenueApi,
    private val eventApi: EventApi,
    private val eventPublisher: ApplicationEventPublisher
) : StaffCartApi {

    private val logger = KotlinLogging.logger {}

    /**
     * Checkout staff cart and create a confirmed booking.
     *
     * Flow:
     * 1. Load cart with all items (already reserved in inventory)
     * 2. Validate cart is not empty
     * 3. Create guest for customer
     * 4. Assemble booking from cart using BookingCreationService
     * 5. Set staff-specific fields (salesChannel, staffId)
     * 6. Save and confirm booking immediately
     * 7. Finalize inventory (RESERVED -> SOLD) and generate tickets
     * 8. Delete cart
     * 9. Send confirmation email
     *
     * @param token Cart token
     * @param request Checkout details (customer info, payment reference, promo code)
     * @param venueId Venue ID (validated at controller level)
     * @param staffId Staff member performing the sale
     * @return Confirmed booking response
     */
    @Transactional
    override fun checkoutStaffCart(
        token: UUID,
        request: StaffCartCheckoutRequest,
        venueId: UUID,
        staffId: UUID
    ): StaffCartCheckoutResponse {
        logger.info { "Staff $staffId checking out cart $token for venue $venueId" }

        // 1. Load cart with all items (already reserved)
        val cart = cartSessionManager.getActiveCartWithItems(token)

        // 2. Validate cart has items
        if (cart.isEmpty()) {
            throw VenuesException.ValidationFailure("Cannot checkout an empty cart")
        }

        val normalizedEmail = request.customerEmail?.trim().takeUnless { it.isNullOrBlank() }
        val normalizedPhone = request.customerPhone?.trim().takeUnless { it.isNullOrBlank() }

        if (normalizedEmail == null && normalizedPhone == null) {
            throw VenuesException.ValidationFailure("Customer email or phone is required")
        }

        // 3. Apply promo code if provided
        if (!request.promoCode.isNullOrBlank()) {
            cart.promoCode = request.promoCode
        }

        // 4. Fetch session info
        val sessionDto = eventApi.getEventSessionInfo(cart.sessionId)
            ?: throw VenuesException.ResourceNotFound("Event session not found")

        // 5. Build cart snapshot
        val cartSnapshot = CartSnapshot(
            cart = cart,
            seats = cart.seats.toList(),
            gaItems = cart.gaItems.toList(),
            tables = cart.tables.toList(),
            session = sessionDto
        )

        // 6. Create guest for customer
        val guest = guestService.findOrCreateGuest(
            normalizedEmail,
            request.customerName,
            normalizedPhone
        )

        // 7. Assemble booking from cart (items already reserved, no re-reservation needed)
        val creationResult = bookingCreationService.assembleBooking(
            snapshot = cartSnapshot,
            context = BookingCreationContext(
                userId = null,  // Staff sale, no user account
                guest = guest,
                platformId = null,
                paymentReference = request.paymentReference
            )
        )

        // 8. Override with staff-specific fields
        val booking = creationResult.booking.apply {
            salesChannel = SalesChannel.DIRECT_SALE
            this.staffId = staffId
            externalOrderNumber = request.paymentReference
        }

        // 9. Save booking
        val savedBooking = bookingRepository.save(booking)

        // 10. Redeem promo if applicable
        bookingFulfillmentService.redeemPromoIfNeeded(savedBooking)

        // 11. Confirm booking immediately (payment assumed received)
        savedBooking.confirm(null)
        val confirmedBooking = bookingRepository.save(savedBooking)

        // 12. Finalize inventory (RESERVED -> SOLD) and record tickets sold
        bookingFulfillmentService.finalizeBookingInventory(confirmedBooking)

        // 13. Generate tickets
        bookingFulfillmentService.generateTickets(confirmedBooking)

        // 14. Delete cart (inventory already finalized)
        cartRepository.delete(cart)

        // 15. Publish event for async email sending
        eventPublisher.publishEvent(
            BookingConfirmedEvent(
                bookingId = confirmedBooking.id,
                venueId = venueId,
                customerEmail = normalizedEmail.orEmpty(),
                customerName = request.customerName,
                locale = guest.preferredLanguage
            )
        )

        logger.info {
            "Staff cart checkout completed: bookingId=${confirmedBooking.id}, " +
                    "cartToken=$token, staffId=$staffId, total=${creationResult.pricing.total}"
        }

        val bookingResponse = bookingService.prepareBookingResponse(confirmedBooking)

        val ticketDelivery = buildTicketDeliveryPayload(
            booking = confirmedBooking,
            sessionDto = sessionDto,
            customerEmail = request.customerEmail,
            localeCode = guest.preferredLanguage ?: "en"
        )

        return StaffCartCheckoutResponse(
            booking = bookingResponse,
            ticketDelivery = ticketDelivery
        )
    }

    private fun buildTicketDeliveryPayload(
        booking: app.venues.booking.domain.Booking,
        sessionDto: app.venues.event.api.dto.EventSessionDto,
        customerEmail: String?,
        localeCode: String
    ): TicketDeliveryPayload {
        val tickets = ticketApi.getTicketsForBooking(booking.id)
        val emailTickets = buildEmailTickets(tickets, localeCode)

        val pdfBytes = if (tickets.isNotEmpty()) {
            val zoneId = ZoneId.systemDefault()
            val eventDate = sessionDto.startTime.atZone(zoneId).toLocalDate().toString()
            val eventTime = sessionDto.startTime.atZone(zoneId).toLocalTime().toString()
            val venueName = booking.venueId?.let { venueApi.getVenueNameTranslated(it, localeCode) }
                ?: "Venues App"
            val bookingReference = booking.externalOrderNumber
                ?: booking.id.toString().take(8).uppercase()

            try {
                pdfTicketService.generateTicketsStripPdf(
                    eventTitle = sessionDto.eventTitle,
                    eventDate = eventDate,
                    eventTime = eventTime,
                    venueName = venueName,
                    bookingReference = bookingReference,
                    tickets = emailTickets,
                    locale = Locale.forLanguageTag(localeCode)
                )
            } catch (ex: Exception) {
                logger.warn(ex) { "Failed to generate tickets PDF for booking ${booking.id}" }
                null
            }
        } else {
            null
        }

        val pdfBase64 = pdfBytes?.let { Base64.getEncoder().encodeToString(it) }
        val pdfFileName = pdfBytes?.let {
            "tickets-${booking.externalOrderNumber ?: booking.id.toString().take(8)}.pdf"
        }

        return TicketDeliveryPayload(
            ticketCount = tickets.size,
            ticketsPdfBase64 = pdfBase64,
            pdfFileName = pdfFileName,
            delivery = TicketDeliveryInfo(
                emailed = true, // confirmation email is dispatched asynchronously
                email = normalizedCustomerEmailOrNull(customerEmail),
                pdfIncluded = pdfBytes != null
            )
        )
    }

    private fun normalizedCustomerEmailOrNull(email: String?): String? {
        val trimmed = email?.trim()
        if (trimmed.isNullOrBlank()) {
            return null
        }
        return trimmed
    }

    private fun buildEmailTickets(
        tickets: List<app.venues.ticket.api.dto.TicketDto>,
        localeCode: String
    ): List<EmailTicket> {
        if (tickets.isEmpty()) {
            return emptyList()
        }

        val languageCode = localeCode.ifBlank { "en" }
        val total = tickets.size

        return tickets.mapIndexed { index, ticket ->
            val seatInfoLines = resolveSeatInfoLines(ticket, languageCode)
            EmailTicket(
                qrCodeBase64 = qrCodeService.generateQrCodeImageBase64(ticket.qrCode),
                ticketType = ticket.ticketType,
                seatInfoLines = seatInfoLines,
                ticketNumber = "Ticket ${index + 1} of $total"
            )
        }
    }

    private fun resolveSeatInfoLines(
        ticket: app.venues.ticket.api.dto.TicketDto,
        languageCode: String
    ): List<String> {
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

    /**
     * Close items from staff cart (seats, tables, GA capacity reduction).
     *
     * Flow:
     * 1. Load cart with all items (already reserved in inventory)
     * 2. Validate cart has items
     * 3. Close seats (set to CLOSED status)
     * 4. Close tables (set to CLOSED status)
     * 5. Reduce GA capacity by quantity (decrement available capacity)
     * 6. Release inventory (RESERVED -> AVAILABLE) since we're closing, not selling
     * 7. Delete cart
     *
     * @param token Cart token
     * @param venueId Venue ID (validated at controller level)
     * @param staffId Staff member performing the close action
     */
    @Transactional
    fun closeCartItems(
        token: UUID,
        venueId: UUID,
        staffId: UUID
    ) {
        logger.info { "Staff $staffId closing items from cart $token for venue $venueId" }

        // 1. Load cart with all items (already reserved)
        val cart = cartSessionManager.getActiveCartWithItems(token)

        // 2. Validate cart has items
        if (cart.isEmpty()) {
            throw VenuesException.ValidationFailure("Cannot close items from an empty cart")
        }

        val sessionId = cart.sessionId

        // 3. Extract seat IDs
        val seatIds = cart.seats.map { it.seatId }

        // 4. Extract table IDs
        val tableIds = cart.tables.map { it.tableId }

        // 5. Extract GA items with quantities
        val gaItems = cart.gaItems.map { it.gaAreaId to it.quantity }

        // 6. Close seats
        if (seatIds.isNotEmpty()) {
            val closedCount = eventApi.closeSeats(sessionId, seatIds)
            logger.info { "Closed $closedCount seats from cart" }
        }

        // 7. Close tables
        if (tableIds.isNotEmpty()) {
            val closedCount = eventApi.closeTables(sessionId, tableIds)
            logger.info { "Closed $closedCount tables from cart" }
        }

        // 8. Reduce GA capacity for each GA area
        gaItems.forEach { (gaAreaId, quantity) ->
            val success = eventApi.reduceGACapacity(sessionId, gaAreaId, quantity)
            if (success) {
                logger.info { "Reduced GA capacity for area $gaAreaId by $quantity" }
            } else {
                logger.warn { "Failed to reduce GA capacity for area $gaAreaId by $quantity (would go below soldCount)" }
            }
        }

        // 9. Release inventory (RESERVED -> AVAILABLE) since we're closing, not selling
        // This releases the reservations made when items were added to cart
        if (seatIds.isNotEmpty()) {
            eventApi.releaseSeatsBatch(sessionId, seatIds)
        }
        if (tableIds.isNotEmpty()) {
            eventApi.releaseTablesBatch(sessionId, tableIds)
        }
        gaItems.forEach { (gaAreaId, quantity) ->
            eventApi.releaseGa(sessionId, gaAreaId, quantity)
        }

        // 10. Delete cart
        cartRepository.delete(cart)

        logger.info {
            "Staff cart close completed: cartToken=$token, staffId=$staffId, " +
                    "seats=${seatIds.size}, tables=${tableIds.size}, gaAreas=${gaItems.size}"
        }
    }
}
