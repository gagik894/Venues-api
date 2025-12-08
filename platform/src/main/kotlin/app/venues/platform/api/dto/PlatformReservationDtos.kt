package app.venues.platform.api.dto

import app.venues.booking.api.dto.PlatformGAReservation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.*

// ===========================================
// PLATFORM RESERVATION REQUEST DTOs
// ===========================================

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

