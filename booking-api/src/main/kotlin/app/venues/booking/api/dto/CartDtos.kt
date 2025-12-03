package app.venues.booking.api.dto

import app.venues.shared.money.MoneyAmount
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
    var sessionId: UUID,

    @field:NotNull(message = "Seat code is required")
    var code: String
)

/**
 * Request to add GA tickets to cart
 */
data class AddGAToCartRequest(
    @field:NotNull(message = "Session ID is required")
    var sessionId: UUID,

    @field:NotNull(message = "GA code is required")
    var code: String,

    @field:Min(value = 1, message = "Quantity must be at least 1")
    @field:Max(value = 10, message = "Maximum 10 tickets per transaction")
    var quantity: Int
)

/**
 * Request to add table to cart
 */
data class AddTableToCartRequest(
    @field:NotNull(message = "Session ID is required")
    var sessionId: UUID,

    @field:NotNull(message = "Table code is required")
    var code: String
)

/**
 * Request to update GA ticket quantity in cart.
 * Contains only the fields that can be updated.
 * Identifiers (token, level) are passed in the URL.
 */
data class UpdateGAQuantityRequest(
    @field:Min(value = 0, message = "Quantity must be at least 0 (0 to remove)")
    @field:Max(value = 10, message = "Maximum 10 tickets per transaction")
    @field:NotNull(message = "Quantity is required")
    var quantity: Int
)


// ===========================================
// CART RESPONSE DTOs
// ===========================================

/**
 * Cart seat item response
 */
data class CartSeatResponse(
    val code: String,
    val number: String?,
    val rowLabel: String?,
    val levelName: String,
    val price: MoneyAmount,
    val priceTemplateName: String?
)

/**
 * Cart GA item response
 */
data class CartGAItemResponse(
    val code: String?,
    val name: String,
    val quantity: Int,
    val unitPrice: MoneyAmount,
    val totalPrice: MoneyAmount,
    val priceTemplateName: String?
)

/**
 * Cart table response
 */
data class CartTableResponse(
    val code: String,
    val number: String,
    val price: MoneyAmount
)

/**
 * Complete cart summary
 */
data class CartSummaryResponse(
    val token: UUID,
    val seats: List<CartSeatResponse>,
    val gaItems: List<CartGAItemResponse>,
    val tables: List<CartTableResponse> = emptyList(),
    val totalPrice: MoneyAmount,
    val currency: String,
    val expiresAt: String,
    val sessionId: UUID,
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

/**
 * Response after applying a promo code
 */
data class PromoCodeAppliedResponse(
    val originalPrice: MoneyAmount,
    val discountAmount: MoneyAmount,
    val finalPrice: MoneyAmount,
    val promoCode: String,
    val message: String
)

