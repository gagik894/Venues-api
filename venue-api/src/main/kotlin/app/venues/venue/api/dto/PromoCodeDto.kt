package app.venues.venue.api.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * DTO for Promo Code details exposed to other modules.
 */
data class PromoCodeDto(
    val id: UUID,
    val code: String,
    val discountType: String, // "PERCENTAGE" or "FIXED_AMOUNT"
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val maxDiscountAmount: BigDecimal?,
    val expiresAt: Instant?,
    val isActive: Boolean
)
