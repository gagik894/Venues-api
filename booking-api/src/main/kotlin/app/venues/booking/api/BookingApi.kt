package app.venues.booking.api

import app.venues.booking.api.dto.BookingResponse
import java.util.*

/**
 * Public API Port for Booking Command (Write) operations.
 *
 * This interface defines the contract for all cross-module interactions
 * that create or manage bookings.
 *
 * External modules (like Platform) MUST depend on this interface,
 * not on the concrete `BookingService` implementation.
 */
interface BookingApi {

    /**
     * Creates a new booking from an existing cart token.
     * This is the primary method for converting a cart into a sale.
     *
     * @param cartToken The UUID of the cart to convert.
     * @param platformId The ID of the external platform (if any) making the sale.
     * @param paymentMethod A string identifying the payment method (e.g., "platform_api").
     * @param paymentReference A reference ID from the external payment system.
     * @param guestEmail Email for the booking confirmation.
     * @param guestName Name for the booking.
     * @param guestPhone Phone for the booking (optional).
     * @return The confirmed Booking entity.
     * @see app.venues.booking.service.BookingService.createBookingFromCart
     */
    fun createBookingFromCart(
        cartToken: UUID,
        platformId: UUID,
        paymentMethod: String,
        paymentReference: String?,
        guestEmail: String,
        guestName: String,
        guestPhone: String?
    ): BookingResponse

    /**
     * Confirms a booking after successful payment.
     * This should finalize the sale and generate tickets.
     *
     * @param bookingId The ID of the booking to confirm.
     */
    fun confirmBooking(bookingId: UUID)

    /**
     * Cancels a booking after failed payment.
     * This should release held seats.
     *
     * @param bookingId The ID of the booking to cancel.
     */
    fun cancelBooking(bookingId: UUID)
}
