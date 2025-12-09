package app.venues.platform.api.dto

import app.venues.booking.api.dto.PlatformGAReservation
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.util.*

// ===========================================
// PLATFORM RESERVATION REQUEST DTOs
// ===========================================

/**
 * Platform "Easy Mode" reserve request (creates PENDING booking).
 */
data class PlatformEasyReserveRequest(
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
    val items: List<PlatformEasyItemRequest>,

    @field:Size(max = 50, message = "Promo code must not exceed 50 characters")
    val promoCode: String? = null
)

/**
 * Item for Platform "Easy Mode" reserve request.
 * Exactly one of seatCode, gaAreaCode, or tableCode must be provided.
 */
data class PlatformEasyItemRequest(
    val seatCode: String? = null,
    val gaAreaCode: String? = null,
    val tableCode: String? = null,

    @field:jakarta.validation.constraints.Min(value = 1, message = "Quantity must be at least 1")
    @field:jakarta.validation.constraints.Max(value = 100, message = "Quantity must not exceed 100")
    val quantity: Int = 1
)

/**
 * Platform API reservation request (from external platform)
 */
data class PlatformReservationRequest(
    @field:NotNull(message = "Session ID is required")
    var sessionId: UUID,

    var reservationToken: UUID? = null,

    @field:Size(min = 1, message = "At least one seat, GA ticket, or table must be specified")
    var seatIdentifiers: List<String>? = null,

    var gaReservations: List<PlatformGAReservation>? = null,

    @field:Size(min = 1, message = "At least one table must be specified")
    var tableIdentifiers: List<String>? = null,

    var guestEmail: String? = null,

    var guestName: String? = null,

    var externalReference: String? = null
)

/**
 * Platform API release request (from external platform)
 * Release a previously held reservation
 */
data class PlatformReleaseRequest(
    @field:NotNull(message = "Reservation token is required")
    var reservationToken: UUID
)

/**
 * Platform API sell request (from external platform)
 * Convert reservation to confirmed booking
 */
data class PlatformSellRequest(
    @field:NotNull(message = "Reservation token is required")
    var reservationToken: UUID,

    @field:NotBlank(message = "Payment method is required")
    var paymentMethod: String,

    @field:NotBlank(message = "Payment reference is required")
    var paymentReference: String,

    var guestEmail: String? = null,

    var guestName: String? = null,

    var guestPhone: String? = null,

    var externalReference: String? = null
)

// ===========================================
// PLATFORM RESERVATION RESPONSE DTOs
// ===========================================

/**
 * Platform reservation response
 */
data class PlatformReservationResponse(
    val reservationToken: UUID,
    val message: String,
    val expiresAt: String,
    val seats: List<ReservedSeatInfo>?,
    val gaTickets: List<ReservedGAInfo>?,
    val tables: List<ReservedTableInfo>?
)

/**
 * Platform release response
 */
data class PlatformReleaseResponse(
    val message: String,
    val releasedSeats: Int,
    val releasedGATickets: Int,
    val releasedTables: Int
)

/**
 * Platform sell response (booking confirmation)
 */
data class PlatformSellResponse(
    val bookingId: String,
    val bookingReference: String,
    val message: String,
    val totalAmount: String,
    val seats: List<ReservedSeatInfo>?,
    val gaTickets: List<ReservedGAInfo>?,
    val tables: List<ReservedTableInfo>?
)

