package app.venues.user.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/**
 * Entity representing a promotional code assigned to/used by a user.
 *
 * This is the user-specific instance of a promo code usage.
 * Links users to the promo codes they've used or have available.
 *
 * Design:
 * - Links users to promo codes (many-to-many)
 * - Tracks usage status and history
 * - Enforces usage limits and expiration
 * - Tracks discount value for analytics
 *
 * Use Cases:
 * - User enters a promo code at checkout
 * - System validates code eligibility
 * - User views their available promo codes
 * - Analytics on promo code effectiveness
 *
 * Note: The actual PromoCode master data should be in a separate module
 * (likely in a shared/marketing module or booking module).
 */
@Entity
@Table(
    name = "user_promo_codes",
    indexes = [
        Index(name = "idx_user_promo_user_id", columnList = "user_id"),
        Index(name = "idx_user_promo_code", columnList = "promo_code"),
        Index(name = "idx_user_promo_status", columnList = "status")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_promo_code",
            columnNames = ["user_id", "promo_code"]
        )
    ]
)
class UserPromoCode(
    /**
     * The user this promo code is assigned to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    /**
     * The promo code string (e.g., "SUMMER2025", "WELCOME10").
     * Must match a valid promo code in the master promo codes table.
     */
    @Column(name = "promo_code", nullable = false, length = 50)
    val promoCode: String,

    /**
     * Current status of this promo code for this user.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PromoCodeStatus = PromoCodeStatus.AVAILABLE,

    /**
     * Type of discount this promo code provides.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val discountType: DiscountType,

    /**
     * Discount value.
     * - For PERCENTAGE: value between 0-100 (e.g., 10 = 10% off)
     * - For FIXED_AMOUNT: actual amount in currency (e.g., 5.00 = $5 off)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    val discountValue: BigDecimal,

    /**
     * Maximum number of times this user can use this promo code.
     * Null means unlimited uses.
     */
    @Column
    val maxUses: Int? = null,

    /**
     * Number of times this user has used this promo code.
     */
    @Column(nullable = false)
    var timesUsed: Int = 0,

    /**
     * Timestamp when this promo code expires for this user.
     * Null means no expiration.
     */
    @Column
    val expiresAt: Instant? = null,
) : AbstractUuidEntity() {
    /**
     * Checks if this promo code is currently valid and can be used.
     *
     * @return true if status is AVAILABLE, not expired, and has uses remaining
     */
    fun isValid(): Boolean {
        if (status != PromoCodeStatus.AVAILABLE) return false
        if (expiresAt?.isBefore(Instant.now()) == true) return false
        if (maxUses != null && timesUsed >= maxUses) return false
        return true
    }

    /**
     * Checks if this promo code has expired.
     *
     * @return true if expiration date has passed
     */
    fun isExpired(): Boolean {
        return expiresAt?.isBefore(Instant.now()) == true
    }

    /**
     * Checks if usage limit has been reached.
     *
     * @return true if max uses reached
     */
    fun hasReachedLimit(): Boolean {
        return maxUses != null && timesUsed >= maxUses
    }

    /**
     * Increments the usage count of this promo code.
     * Updates status to EXHAUSTED if limit is reached.
     */
    fun use() {
        timesUsed++
        if (hasReachedLimit()) {
            status = PromoCodeStatus.EXHAUSTED
        }
    }

    /**
     * Calculates the discount amount for a given order total.
     *
     * @param orderTotal Total amount before discount
     * @return Discount amount to subtract
     */
    fun calculateDiscount(orderTotal: BigDecimal): BigDecimal {
        return when (discountType) {
            DiscountType.PERCENTAGE -> {
                orderTotal.multiply(discountValue).divide(BigDecimal(100))
            }

            DiscountType.FIXED_AMOUNT -> {
                discountValue.min(orderTotal) // Can't discount more than order total
            }
        }
    }
}

/**
 * Status of a promo code for a specific user.
 */
enum class PromoCodeStatus {
    /** Available for use */
    AVAILABLE,

    /** Has been used at least once (but may have more uses available) */
    USED,

    /** All uses exhausted */
    EXHAUSTED,

    /** Expired */
    EXPIRED,

    /** Revoked by admin */
    REVOKED
}

/**
 * Type of discount provided by a promo code.
 */
enum class DiscountType {
    /** Percentage discount (e.g., 10% off) */
    PERCENTAGE,

    /** Fixed amount discount (e.g., $5 off) */
    FIXED_AMOUNT
}

