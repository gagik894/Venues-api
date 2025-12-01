package app.venues.booking.service

import app.venues.booking.api.StaffCartApi
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.StaffCartCheckoutRequest
import app.venues.booking.domain.SalesChannel
import app.venues.booking.event.BookingConfirmedEvent
import app.venues.booking.manager.CartSessionManager
import app.venues.booking.repository.BookingRepository
import app.venues.booking.repository.CartRepository
import app.venues.booking.service.model.BookingCreationContext
import app.venues.booking.service.model.CartSnapshot
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    ): BookingResponse {
        logger.info { "Staff $staffId checking out cart $token for venue $venueId" }

        // 1. Load cart with all items (already reserved)
        val cart = cartSessionManager.getActiveCartWithItems(token)

        // 2. Validate cart has items
        if (cart.isEmpty()) {
            throw VenuesException.ValidationFailure("Cannot checkout an empty cart")
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
            request.customerEmail,
            request.customerName,
            request.customerPhone
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
                customerEmail = request.customerEmail,
                customerName = request.customerName,
                locale = guest.preferredLanguage
            )
        )

        logger.info {
            "Staff cart checkout completed: bookingId=${confirmedBooking.id}, " +
                    "cartToken=$token, staffId=$staffId, total=${creationResult.pricing.total}"
        }

        return bookingService.prepareBookingResponse(confirmedBooking)
    }
}
