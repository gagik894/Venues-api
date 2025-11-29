package app.venues.booking.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.util.*

// ===========================================
// DIRECT SALE REQUEST DTOs
// ===========================================

/**
 * Request for staff to create a direct sale (bypassing cart flow).
 * Creates a CONFIRMED booking immediately with inventory finalized.
 */
data class DirectSaleRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: UUID,

    @field:NotBlank(message = "Customer email is required")
    @field:Email(message = "Invalid email format")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val customerEmail: String,

    @field:NotBlank(message = "Customer name is required")
    @field:Size(max = 200, message = "Name must not exceed 200 characters")
    val customerName: String,

    @field:Size(max = 20, message = "Phone must not exceed 20 characters")
    val customerPhone: String? = null,

    @field:NotEmpty(message = "At least one item is required")
    @field:Valid
    val items: List<DirectSaleItemRequest>,

    @field:Size(max = 100, message = "Payment reference must not exceed 100 characters")
    val paymentReference: String? = null,

    @field:Size(max = 50, message = "Promo code must not exceed 50 characters")
    val promoCode: String? = null
)

/**
 * Individual item in a direct sale request.
 * Exactly one of seatCode, gaAreaCode, or tableCode must be provided.
 */
data class DirectSaleItemRequest(
    /** Seat code for reserved seating. */
    val seatCode: String? = null,

    /** GA area code for general admission. */
    val gaAreaCode: String? = null,

    /** Table code for table booking. */
    val tableCode: String? = null,

    /** Quantity (only applicable for GA items, defaults to 1 for seats/tables). */
    @field:Min(value = 1, message = "Quantity must be at least 1")
    @field:Max(value = 100, message = "Quantity must not exceed 100")
    val quantity: Int = 1
)
