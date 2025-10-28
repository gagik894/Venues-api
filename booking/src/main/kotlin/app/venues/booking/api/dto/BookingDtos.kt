package app.venues.booking.api.dto

import app.venues.booking.domain.BookingStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.*

// ===========================================
// CHECKOUT REQUEST DTOs
// ===========================================

/**
 * Checkout request to convert cart to booking
 */
data class CheckoutRequest(
    @field:NotNull(message = "Reservation token is required")
    val token: UUID,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Name is required")
    @field:Size(max = 200, message = "Name must not exceed 200 characters")
    val name: String,

    @field:Size(max = 20, message = "Phone must not exceed 20 characters")
    val phone: String? = null
)

/**
 * Confirm booking payment request
 */
data class ConfirmBookingRequest(
    @field:Size(max = 100, message = "Payment ID must not exceed 100 characters")
    val paymentId: String? = null
)

/**
 * Cancel booking request
 */
data class CancelBookingRequest(
    @field:Size(max = 500, message = "Reason must not exceed 500 characters")
    val reason: String? = null
)

// ===========================================
// BOOKING RESPONSE DTOs
// ===========================================

/**
 * Booking item response
 */
data class BookingItemResponse(
    val id: Long,
    val seatId: Long?,
    val seatIdentifier: String?,
    val levelId: Long?,
    val levelName: String?,
    val quantity: Int,
    val unitPrice: String,
    val totalPrice: String,
    val priceTemplateName: String?
)

/**
 * Booking response
 */
data class BookingResponse(
    val id: String,  // UUID as string
    val sessionId: Long,
    val eventTitle: String,
    val eventDescription: String?,
    val venueName: String,
    val sessionStartTime: String,
    val sessionEndTime: String,
    val customerEmail: String,
    val customerName: String,
    val items: List<BookingItemResponse>,
    val totalPrice: String,
    val currency: String,
    val status: BookingStatus,
    val confirmedAt: String?,
    val cancelledAt: String?,
    val cancellationReason: String?,
    val paymentId: String?,
    val createdAt: String
)

/**
 * Checkout response
 */
data class CheckoutResponse(
    val booking: BookingResponse,
    val message: String
)

