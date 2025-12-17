package app.venues.platform.api.dto

import app.venues.booking.api.dto.PlatformGAReservation
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import java.util.*

// ===========================================
// PLATFORM BOOKING FLOW DTOs (/hold, /checkout, /confirm)
// ===========================================

/**
 * Platform /hold request - Create cart and reserve inventory
 */
data class PlatformHoldRequest(
    @field:NotNull(message = "Session ID is required")
    var sessionId: UUID,

    var holdToken: UUID? = null,

    var seatIdentifiers: List<String>? = null,

    var gaReservations: List<PlatformGAReservation>? = null,

    var tableIdentifiers: List<String>? = null,

    var ttlSeconds: Long? = null
)

/**
 * Platform /hold response - Cart created with reserved inventory
 */
data class PlatformHoldResponse(
    val holdToken: UUID,
    val expiresAt: String,
    val seats: List<ReservedSeatInfo>,
    val gaTickets: List<ReservedGAInfo>,
    val tables: List<ReservedTableInfo>,
    val totalPrice: String,
    val currency: String
)

/**
 * Platform /checkout request - Validate cart and prepare for payment
 */
data class PlatformCheckoutRequest(
    @field:NotNull(message = "Hold token is required")
    var holdToken: UUID,

    @field:Email(message = "Invalid email format")
    var guestEmail: String? = null,

    var guestName: String? = null,

    var guestPhone: String? = null,

    var externalReference: String? = null
)

/**
 * Platform /checkout response - Cart validated, ready for payment
 */
data class PlatformCheckoutResponse(
    val holdToken: UUID,
    val totalPrice: String,
    val currency: String,
    val expiresAt: String,
    val seats: List<ReservedSeatInfo>,
    val gaTickets: List<ReservedGAInfo>,
    val tables: List<ReservedTableInfo>,
    val guestEmail: String,
    val guestName: String
)

/**
 * Platform /confirm request - Confirm booking with payment proof
 */
data class PlatformConfirmRequest(
    @field:NotNull(message = "Hold token is required")
    var holdToken: UUID,
    var paymentReference: String? = null,
    @field:Email(message = "Invalid email format")
    var guestEmail: String? = null,
    var guestName: String? = null,
    var guestPhone: String? = null
)

/**
 * Platform /confirm response - Booking confirmed
 */
data class PlatformConfirmResponse(
    val bookingId: UUID,
    val bookingReference: String,
    val status: String,
    val totalAmount: String,
    val currency: String,
    val confirmedAt: String,
    val seats: List<ReservedSeatInfo>,
    val gaTickets: List<ReservedGAInfo>,
    val tables: List<ReservedTableInfo>
)

