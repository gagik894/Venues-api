package app.venues.platform.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.util.*

// ===========================================
// PLATFORM EASY FLOW DTOs (PENDING booking)
// ===========================================

data class PlatformEasyReserveRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: UUID,

    @field:NotBlank(message = "Customer email is required")
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

data class PlatformEasyItemRequest(
    val seatCode: String? = null,
    val gaAreaCode: String? = null,
    val tableCode: String? = null,

    @field:Min(value = 1, message = "Quantity must be at least 1")
    @field:Max(value = 100, message = "Quantity must not exceed 100")
    val quantity: Int = 1
)

// ===========================================
// PLATFORM RELEASE DTOs
// ===========================================

data class PlatformReleaseRequest(
    @field:NotNull(message = "Reservation token is required")
    var reservationToken: UUID
)

data class PlatformReleaseResponse(
    val message: String,
    val releasedSeats: Int,
    val releasedGATickets: Int,
    val releasedTables: Int
)

