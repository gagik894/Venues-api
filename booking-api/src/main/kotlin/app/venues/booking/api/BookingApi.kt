package app.venues.booking.api

import app.venues.booking.api.dto.BookingResponse
import app.venues.booking.api.dto.DirectSaleRequest
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
        paymentReference: String? = null,
        guestEmail: String? = null,
        guestName: String? = null,
        guestPhone: String? = null
    ): BookingResponse

    /**
     * Confirms a booking after successful payment.
     * This should finalize the sale and generate tickets.
     *
     * @param bookingId The ID of the booking to confirm.
     * @param paymentId The ID of the successful payment transaction.
     */
    fun confirmBooking(bookingId: UUID, paymentId: UUID)

    /**
     * Confirms a platform booking (payment handled externally).
     *
     * @param bookingId The ID of the booking to confirm.
     */
    fun confirmPlatformBooking(bookingId: UUID)

    /**
     * Cancels a booking after failed payment.
     * This should release held seats.
     *
     * @param bookingId The ID of the booking to cancel.
     */
    fun cancelBooking(bookingId: UUID)

    /**
     * Refunds a booking and updates its status.
     *
     * @param bookingId The ID of the booking to refund.
     * @param reason An optional reason for the refund.
     */
    fun refundBooking(bookingId: UUID, reason: String?)

    /**
     * Retrieves a booking by its ID.
     *
     * @param bookingId The unique identifier of the booking.
     * @return The booking response.
     * @throws app.venues.common.exception.VenuesException.ResourceNotFound if not found.
     */
    fun getBookingById(bookingId: UUID): BookingResponse

    /**
     * Creates a confirmed platform booking directly (skip cart).
     * Uses platform sales channel and optional guest info.
     */
    fun createPlatformDirectBooking(
        request: DirectSaleRequest,
        platformId: UUID,
        guestEmail: String? = null,
        guestName: String? = null,
        guestPhone: String? = null,
        confirmBooking: Boolean = true
    ): BookingResponse
}
