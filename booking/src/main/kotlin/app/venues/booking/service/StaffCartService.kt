package app.venues.booking.service

import app.venues.booking.api.StaffCartApi
import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.DirectSaleItemRequest
import app.venues.booking.api.dto.DirectSaleRequest
import app.venues.booking.api.dto.StaffCartCheckoutRequest
import app.venues.booking.manager.CartSessionManager
import app.venues.booking.repository.CartRepository
import app.venues.common.exception.VenuesException
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
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
 * - Provides checkout functionality that converts cart to DirectSaleRequest
 * - Delegates booking creation to DirectSalesService for consistency
 * - Cleans up cart after successful checkout
 */
@Service
@Transactional
class StaffCartService(
    private val cartSessionManager: CartSessionManager,
    private val cartRepository: CartRepository,
    private val directSalesService: DirectSalesService,
    private val seatingApi: SeatingApi
) : StaffCartApi {

    private val logger = KotlinLogging.logger {}

    /**
     * Checkout staff cart and create a confirmed booking.
     *
     * Flow:
     * 1. Load cart with all items
     * 2. Validate cart is not empty
     * 3. Build DirectSaleRequest from cart contents
     * 4. Delegate to DirectSalesService for booking creation
     * 5. Delete cart (inventory already finalized by DirectSalesService)
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

        // 1. Load cart with all items (using @EntityGraph for performance)
        val cart = cartSessionManager.getActiveCartWithItems(token)

        // 2. Validate cart has items
        if (cart.isEmpty()) {
            throw VenuesException.ValidationFailure("Cannot checkout an empty cart")
        }

        // 3. Build items list for DirectSaleRequest
        val items = mutableListOf<DirectSaleItemRequest>()

        // Add seats
        cart.seats.forEach { seat ->
            val seatInfo = seatingApi.getSeatInfo(seat.seatId)
                ?: throw VenuesException.ResourceNotFound("Seat not found: ${seat.seatId}")

            items.add(
                DirectSaleItemRequest(
                    seatCode = seatInfo.code,
                    quantity = 1
                )
            )
        }

        // Add GA items
        cart.gaItems.forEach { gaItem ->
            val gaInfo = seatingApi.getGaInfo(gaItem.gaAreaId)
                ?: throw VenuesException.ResourceNotFound("GA area not found: ${gaItem.gaAreaId}")

            items.add(
                DirectSaleItemRequest(
                    gaAreaCode = gaInfo.code,
                    quantity = gaItem.quantity
                )
            )
        }

        // Add tables
        cart.tables.forEach { table ->
            val tableInfo = seatingApi.getTableInfo(table.tableId)
                ?: throw VenuesException.ResourceNotFound("Table not found: ${table.tableId}")

            items.add(
                DirectSaleItemRequest(
                    tableCode = tableInfo.code,
                    quantity = 1
                )
            )
        }

        // 4. Build DirectSaleRequest
        val directSaleRequest = DirectSaleRequest(
            sessionId = cart.sessionId,
            customerEmail = request.customerEmail,
            customerName = request.customerName,
            customerPhone = request.customerPhone,
            items = items,
            paymentReference = request.paymentReference,
            promoCode = request.promoCode
        )

        // 5. Create booking via DirectSalesService
        // This handles: inventory finalization, promo redemption, booking confirmation
        val booking = directSalesService.createDirectSale(directSaleRequest, venueId, staffId)

        // 6. Delete cart (inventory already moved from RESERVED to SOLD)
        // We don't need to release inventory since DirectSalesService finalized it
        cartRepository.delete(cart)

        logger.info {
            "Staff cart checkout completed: bookingId=${booking.id}, " +
                    "cartToken=$token, staffId=$staffId, itemCount=${items.size}"
        }

        return booking
    }
}
