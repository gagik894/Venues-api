package app.venues.platform.api.dto

import app.venues.platform.domain.PlatformStatus
import app.venues.platform.domain.WebhookEventType
import app.venues.platform.domain.WebhookStatus
import jakarta.validation.constraints.*
import java.util.*

// ===========================================
// PLATFORM REQUEST DTOs
// ===========================================

/**
 * Request to create a new platform
 */
data class CreatePlatformRequest(
    @field:NotBlank(message = "Platform name is required")
    @field:Size(max = 100, message = "Platform name must not exceed 100 characters")
    var name: String,

    @field:NotBlank(message = "API URL is required")
    @field:Size(max = 500, message = "API URL must not exceed 500 characters")
    @field:Pattern(regexp = "^https://.*", message = "API URL must use HTTPS")
    var apiUrl: String,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    var description: String? = null,

    @field:Email(message = "Invalid email format")
    var contactEmail: String? = null,

    @field:Min(value = 1, message = "Rate limit must be at least 1")
    @field:Max(value = 10000, message = "Rate limit must not exceed 10000")
    var rateLimit: Int? = null,

    var webhookEnabled: Boolean = true
)

/**
 * Request to update a platform
 */
data class UpdatePlatformRequest(
    @field:Size(max = 500, message = "API URL must not exceed 500 characters")
    @field:Pattern(regexp = "^$|^https://.*", message = "API URL must be empty or use HTTPS")
    var apiUrl: String? = null,

    var status: PlatformStatus? = null,

    var webhookEnabled: Boolean? = null,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    var description: String? = null,

    @field:Email(message = "Invalid email format")
    var contactEmail: String? = null,

    @field:Min(value = 1, message = "Rate limit must be at least 1")
    @field:Max(value = 10000, message = "Rate limit must not exceed 10000")
    var rateLimit: Int? = null
)

/**
 * Request to regenerate platform shared secret
 */
data class RegenerateSecretRequest(
    @field:NotNull(message = "Confirmation is required")
    var confirm: Boolean
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
 * GA reservation details
 */
data class PlatformGAReservation(
    @field:NotBlank(message = "Level identifier is required")
    var levelIdentifier: String,

    @field:Min(value = 1, message = "Quantity must be at least 1")
    var quantity: Int
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
// NEW PLATFORM API DTOs (/hold, /checkout, /confirm)
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

    var tableIdentifiers: List<String>? = null
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

    @field:NotBlank(message = "Guest email is required")
    @field:Email(message = "Invalid email format")
    var guestEmail: String,

    @field:NotBlank(message = "Guest name is required")
    var guestName: String,

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

    @field:NotBlank(message = "Payment method is required")
    var paymentMethod: String,

    @field:NotBlank(message = "Payment reference is required")
    var paymentReference: String,

    var paymentProofUrl: String? = null
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

// ===========================================
// PLATFORM RESPONSE DTOs
// ===========================================

/**
 * Platform details response
 */
data class PlatformResponse(
    val id: UUID,
    val name: String,
    val apiUrl: String,
    val status: PlatformStatus,
    val webhookEnabled: Boolean,
    val description: String?,
    val contactEmail: String?,
    val rateLimit: Int?,
    val webhookSuccessCount: Long,
    val webhookFailureCount: Long,
    val lastWebhookSuccess: String?,
    val lastWebhookFailure: String?,
    val createdAt: String,
    val lastModifiedAt: String
)

/**
 * Platform with shared secret (only shown once after creation or regeneration)
 */
data class PlatformWithSecretResponse(
    val id: UUID,
    val name: String,
    val apiUrl: String,
    val sharedSecret: String,  // Only included in this response
    val status: PlatformStatus,
    val webhookEnabled: Boolean,
    val description: String?,
    val contactEmail: String?,
    val rateLimit: Int?,
    val createdAt: String
)

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
 * Reserved seat info
 */
data class ReservedSeatInfo(
    val seatIdentifier: String,
    val levelName: String,
    val seatNumber: String?,
    val rowLabel: String?
)

/**
 * Reserved GA info
 */
data class ReservedGAInfo(
    val levelIdentifier: String,
    val levelName: String,
    val quantity: Int
)

/**
 * Reserved table info
 */
data class ReservedTableInfo(
    val tableIdentifier: String,
    val tableName: String
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

/**
 * Webhook event response
 */
data class WebhookEventResponse(
    val id: UUID,
    val platformId: UUID,
    val platformName: String,
    val eventType: WebhookEventType,
    val sessionId: UUID,
    val seatIdentifier: String?,
    val levelIdentifier: String?,
    val tableIdentifier: String?,
    val status: WebhookStatus,
    val responseCode: Int?,
    val errorMessage: String?,
    val attemptCount: Int,
    val nextRetryAt: String?,
    val createdAt: String
)

// ===========================================
// WEBHOOK PAYLOAD DTOs (Sent to platforms)
// ===========================================

/**
 * Base webhook payload
 */
interface WebhookPayload {
    val eventType: WebhookEventType
    val timestamp: String
    val sessionId: UUID
}

/**
 * Seat reserved webhook payload
 */
data class SeatReservedPayload(
    override val eventType: WebhookEventType = WebhookEventType.SEAT_RESERVED,
    override val timestamp: String,
    override val sessionId: UUID,
    val seatIdentifier: String,
) : WebhookPayload

/**
 * Seat released webhook payload
 */
data class SeatReleasedPayload(
    override val eventType: WebhookEventType = WebhookEventType.SEAT_RELEASED,
    override val timestamp: String,
    override val sessionId: UUID,
    val seatIdentifier: String,
) : WebhookPayload

/**
 * GA availability changed webhook payload
 */
data class GAAvailabilityChangedPayload(
    override val eventType: WebhookEventType = WebhookEventType.GA_AVAILABILITY_CHANGED,
    override val timestamp: String,
    override val sessionId: UUID,
    val levelIdentifier: String,
    val levelName: String,
    val availableTickets: Int,
    val totalCapacity: Int
) : WebhookPayload

/**
 * Table reserved webhook payload
 */
data class TableReservedPayload(
    override val eventType: WebhookEventType = WebhookEventType.TABLE_RESERVED,
    override val timestamp: String,
    override val sessionId: UUID,
    val tableIdentifier: String,
    val tableName: String
) : WebhookPayload

/**
 * Table released webhook payload
 */
data class TableReleasedPayload(
    override val eventType: WebhookEventType = WebhookEventType.TABLE_RELEASED,
    override val timestamp: String,
    override val sessionId: UUID,
    val tableIdentifier: String,
    val tableName: String
) : WebhookPayload

