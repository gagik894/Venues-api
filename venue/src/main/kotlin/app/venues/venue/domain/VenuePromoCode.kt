package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant

/**
 * Entity representing a promotional discount code offered by a venue.
 *
 * Venues can create promotional codes to offer discounts to customers.
 * Supports both percentage and fixed amount discounts with usage limits.
 */
@Entity
@Table(
    name = "venue_promo_codes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_promo_code_venue_code",
            columnNames = ["venue_id", "code"]
        )
    ],
    indexes = [
        Index(name = "idx_venue_promo_code_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_promo_code_code", columnList = "code"),
        Index(name = "idx_venue_promo_code_active", columnList = "is_active"),
        Index(name = "idx_venue_promo_code_expires", columnList = "expires_at")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenuePromoCode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The venue offering this promo code
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * The promotional code (e.g., "SUMMER2024", "WELCOME10")
     */
    @Column(name = "code", nullable = false, length = 50)
    var code: String,

    /**
     * Description of what this promo code offers
     */
    @Column(name = "description", length = 255)
    var description: String? = null,

    /**
     * Type of discount (PERCENTAGE or FIXED_AMOUNT)
     */
    @Column(name = "discount_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var discountType: DiscountType,

    /**
     * Discount value (percentage or fixed amount)
     */
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    var discountValue: BigDecimal,

    /**
     * Minimum order amount required to use this code
     */
    @Column(name = "min_order_amount", precision = 10, scale = 2)
    var minOrderAmount: BigDecimal? = null,

    /**
     * Maximum discount amount (useful for percentage discounts)
     */
    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    var maxDiscountAmount: BigDecimal? = null,

    /**
     * Maximum number of times this code can be used (null = unlimited)
     */
    @Column(name = "max_usage_count")
    var maxUsageCount: Int? = null,

    /**
     * Current number of times this code has been used
     */
    @Column(name = "current_usage_count", nullable = false)
    var currentUsageCount: Int = 0,

    /**
     * When this promo code expires (null = no expiration)
     */
    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    /**
     * Whether this promo code is currently active
     */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    // ===========================================
    // Audit Fields
    // ===========================================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    /**
     * Check if this promo code is currently valid for use
     */
    fun isValidForUse(): Boolean {
        if (!isActive) return false
        if (expiresAt?.isBefore(Instant.now()) == true) return false
        if (maxUsageCount != null && currentUsageCount >= maxUsageCount!!) return false
        return true
    }

    /**
     * Calculate discount amount for a given order total
     */
    fun calculateDiscount(orderAmount: BigDecimal): BigDecimal {
        if (!isValidForUse()) return BigDecimal.ZERO
        if (minOrderAmount != null && orderAmount < minOrderAmount!!) return BigDecimal.ZERO

        val discount = when (discountType) {
            DiscountType.PERCENTAGE -> orderAmount * (discountValue / BigDecimal(100))
            DiscountType.FIXED_AMOUNT -> discountValue
        }

        // Apply maximum discount limit if set
        return if (maxDiscountAmount != null && discount > maxDiscountAmount!!) {
            maxDiscountAmount!!
        } else {
            discount
        }
    }
}

/**
 * Types of discounts supported by promo codes
 */
enum class DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT
}
