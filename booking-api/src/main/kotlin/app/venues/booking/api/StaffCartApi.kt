package app.venues.booking.api

import app.venues.booking.api.dto.StaffCartCheckoutRequest
import app.venues.booking.api.dto.StaffCartCheckoutResponse
import java.util.*

/**
 * Public API Port for Staff Cart operations.
 *
 * Provides checkout functionality to convert staff carts into confirmed bookings.
 * Staff carts are used for in-person direct sales at the venue.
 */
interface StaffCartApi {

    /**
     * Checkout staff cart and create a confirmed booking.
     *
     * Converts the cart contents into a direct sale booking, bypassing the
     * payment flow since staff have already collected payment.
     *
     * @param token Cart token
     * @param request Checkout request with customer details and payment reference
     * @param venueId Venue ID (validated by controller)
     * @param staffId Staff member performing the sale
     * @return Confirmed booking response
     */
    fun checkoutStaffCart(
        token: UUID,
        request: StaffCartCheckoutRequest,
        venueId: UUID,
        staffId: UUID
    ): StaffCartCheckoutResponse
}
