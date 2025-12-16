package app.venues.booking.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ===========================================
// STAFF CART REQUEST DTOs
// ===========================================

/**
 * Request to checkout a staff cart and create a confirmed booking.
 *
 * Used for direct sales where staff has already collected payment
 * and needs to finalize the booking immediately.
 */
data class StaffCartCheckoutRequest(
    @field:Email(message = "Invalid email format")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val customerEmail: String?,

    @field:NotBlank(message = "Customer name is required")
    @field:Size(max = 200, message = "Name must not exceed 200 characters")
    val customerName: String,

    @field:Size(max = 20, message = "Phone must not exceed 20 characters")
    val customerPhone: String? = null,

    @field:Size(max = 100, message = "Payment reference must not exceed 100 characters")
    val paymentReference: String? = null,

    @field:Size(max = 50, message = "Promo code must not exceed 50 characters")
    val promoCode: String? = null
)

/**
 * Ticket delivery metadata to indicate where tickets were sent.
 */
data class TicketDeliveryInfo(
    val emailed: Boolean,
    val email: String?,
    val pdfIncluded: Boolean
)

/**
 * Ticket delivery payload returned to staff checkout.
 */
data class TicketDeliveryPayload(
    val ticketCount: Int,
    val ticketsPdfBase64: String?,
    val pdfFileName: String?,
    val delivery: TicketDeliveryInfo
)

/**
 * Response for staff cart checkout including printable tickets.
 */
data class StaffCartCheckoutResponse(
    val booking: BookingResponse,
    val ticketDelivery: TicketDeliveryPayload
)
