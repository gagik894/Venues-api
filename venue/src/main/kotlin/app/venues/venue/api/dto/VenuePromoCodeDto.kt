package app.venues.venue.api.dto

import app.venues.venue.domain.DiscountType
import jakarta.validation.constraints.*
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/**
 * Request DTO for creating venue promo code.
 */
@Serializable
data class VenuePromoCodeRequest(
    /**
     * The promotional code (e.g., "SUMMER2024", "WELCOME10")
     */
    @field:NotBlank(message = "Promo code is required")
    @field:Size(min = 3, max = 50, message = "Promo code must be between 3 and 50 characters")
    @field:Pattern(
        regexp = "^[A-Z0-9_-]+$",
        message = "Promo code can only contain uppercase letters, numbers, underscore and dash"
    )
    val code: String,

    /**
     * Description of what this promo code offers
     */
    @field:Size(max = 255, message = "Description must not exceed 255 characters")
    val description: String? = null,

    /**
     * Type of discount (PERCENTAGE or FIXED_AMOUNT)
     */
    @field:NotNull(message = "Discount type is required")
    val discountType: DiscountType,

    /**
     * Discount value (percentage or fixed amount)
     */
    @field:NotNull(message = "Discount value is required")
    @field:DecimalMin(value = "0.01", message = "Discount value must be positive")
    @field:DecimalMax(value = "999999.99", message = "Discount value too large")
    val discountValue: BigDecimal,

    /**
     * Minimum order amount required to use this code
     */
    @field:DecimalMin(value = "0.01", message = "Minimum order amount must be positive")
    val minOrderAmount: BigDecimal? = null,

    /**
     * Maximum discount amount (useful for percentage discounts)
     */
    @field:DecimalMin(value = "0.01", message = "Maximum discount amount must be positive")
    val maxDiscountAmount: BigDecimal? = null,

    /**
     * Maximum number of times this code can be used (null = unlimited)
     */
    @field:Positive(message = "Max usage count must be positive")
    val maxUsageCount: Int? = null,

    /**
     * When this promo code expires (null = no expiration)
     */
    @field:Future(message = "Expiration date must be in the future")
    val expiresAt: Instant? = null
)

/**
 * Response DTO for venue promo code.
 */
@Serializable
data class VenuePromoCodeResponse(
    val id: Long,
    val code: String,
    val description: String?,
    val discountType: DiscountType,
    val discountValue: String, // Serialized as string to avoid precision issues
    val minOrderAmount: String?,
    val maxDiscountAmount: String?,
    val maxUsageCount: Int?,
    val currentUsageCount: Int,
    val expiresAt: Instant?,
    val isActive: Boolean,
    val createdAt: Instant
)
