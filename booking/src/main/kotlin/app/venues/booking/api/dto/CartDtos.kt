package app.venues.booking.api.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.util.*

// ===========================================
// CART REQUEST DTOs
// ===========================================

/**
 * Request to add seat to cart
 */
data class AddSeatToCartRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: Long,

    @field:NotNull(message = "Seat ID is required")
    val seatId: Long
)

/**
 * Request to add GA tickets to cart
 */
data class AddGAToCartRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: Long,

    @field:NotNull(message = "Level ID is required")
    val levelId: Long,

    @field:Min(value = 1, message = "Quantity must be at least 1")
    @field:Max(value = 10, message = "Maximum 10 tickets per transaction")
    val quantity: Int
)

// ===========================================
// CART RESPONSE DTOs
// ===========================================

/**
 * Cart seat item response
 */
data class CartSeatResponse(
    val id: Long,
    val seatId: Long,
    val seatIdentifier: String,
    val seatNumber: String?,
    val rowLabel: String?,
    val levelName: String,
    val price: String,
    val priceTemplateName: String?
)

/**
 * Cart GA item response
 */
data class CartGAItemResponse(
    val id: Long,
    val levelId: Long,
    val levelName: String,
    val quantity: Int,
    val unitPrice: String,
    val totalPrice: String,
    val priceTemplateName: String?
)

/**
 * Complete cart summary
 */
data class CartSummaryResponse(
    val token: UUID,
    val seats: List<CartSeatResponse>,
    val gaItems: List<CartGAItemResponse>,
    val totalPrice: String,
    val currency: String,
    val expiresAt: String,
    val sessionId: Long,
    val eventTitle: String
)

/**
 * Response after adding item to cart
 */
data class AddToCartResponse(
    val token: UUID,
    val message: String,
    val expiresAt: String
)

