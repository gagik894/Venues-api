package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant

/**
 * Venue Promo Code Entity
 *
 * Represents promotional codes offered by venues for discounts.
 *
 * Features:
 * - Percentage or fixed amount discounts
 * - Expiration dates
 * - Usage limits
 * - Active/inactive status
 */
@Entity
@Table(
    name = "venue_promo_codes",
    indexes = [
        Index(name = "idx_venue_promo_code_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_promo_code_code", columnList = "code"),
        Index(name = "idx_venue_promo_code_active", columnList = "is_active")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_venue_promo_code_venue_code", columnNames = ["venue_id", "code"])
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenuePromoCode(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * The promo code string (e.g., "SUMMER2025")
     */
    @Column(nullable = false, length = 50)
    var code: String,

    /**
     * Description of the promo code
     */
    @Column(length = 255)
    var description: String? = null,

    /**
     * Discount type: PERCENTAGE or FIXED_AMOUNT
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var discountType: DiscountType = DiscountType.PERCENTAGE,

    /**
     * Discount value (percentage or fixed amount)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    var discountValue: BigDecimal,

    /**
     * Minimum order amount required to use this promo code
     */
    @Column(precision = 10, scale = 2)
    var minOrderAmount: BigDecimal? = null,

    /**
     * Maximum discount amount (for percentage discounts)
     */
    @Column(precision = 10, scale = 2)
    var maxDiscountAmount: BigDecimal? = null,

    /**
     * Maximum number of times this code can be used (null = unlimited)
     */
    @Column
    var maxUsageCount: Int? = null,

    /**
     * Current usage count
     */
    @Column(nullable = false)
    var currentUsageCount: Int = 0,

    /**
     * Expiration date (null = no expiration)
     */
    @Column
    var expiresAt: Instant? = null,

    /**
     * Active status
     */
    @Column(nullable = false)
    var isActive: Boolean = true,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    /**
     * Check if promo code is currently valid
     */
    fun isValid(): Boolean {
        if (!isActive) return false
        if (expiresAt?.isBefore(Instant.now()) == true) return false
        if (maxUsageCount != null && currentUsageCount >= maxUsageCount!!) return false
        return true
    }

    /**
     * Increment usage count
     */
    fun incrementUsage() {
        currentUsageCount++
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VenuePromoCode

        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "VenuePromoCode(id=$id, code='$code', discountType=$discountType, discountValue=$discountValue)"
    }
}

/**
 * Discount Type Enum
 */
enum class DiscountType {
    /**
     * Percentage discount (e.g., 20% off)
     */
    PERCENTAGE,

    /**
     * Fixed amount discount (e.g., $10 off)
     */
    FIXED_AMOUNT
}

