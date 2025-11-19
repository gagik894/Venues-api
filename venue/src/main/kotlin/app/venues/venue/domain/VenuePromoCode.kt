package app.venues.venue.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/**
 * A "root" definition for a promotional code created by a Venue.
 * This is referenced by `UserPromoCode`, so it uses a UUID.
 *
 * @param venue The venue that owns this promo code.
 * @param code The unique code string (e.g., "SUMMER20").
 * @param discountType The type of discount.
 * @param discountValue The value of the discount.
 * @param description A description of the promo code.
 * @param minOrderAmount Minimum order amount to apply the promo code.
 * @param maxDiscountAmount Maximum discount amount that can be applied.
 * @param maxUsageCount Maximum number of times this code can be used.
 * @param expiresAt The expiration date/time of the promo code.
 */
@Entity
@Table(
    name = "venue_promo_codes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_promo_code_venue_code",
            columnNames = ["venue_id", "code"]
        )
    ]
)
class VenuePromoCode(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    @Column(name = "code", nullable = false, length = 50)
    var code: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    var discountType: DiscountType,

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    var discountValue: BigDecimal,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    var minOrderAmount: BigDecimal? = null,

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    var maxDiscountAmount: BigDecimal? = null,

    @Column(name = "max_usage_count")
    var maxUsageCount: Int? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    ) : AbstractUuidEntity() {

    @Column(name = "current_usage_count", nullable = false)
    @Access(AccessType.FIELD)
    var currentUsageCount: Int = 0
        protected set

    @Column(name = "is_active", nullable = false)
    @Access(AccessType.FIELD)
    var isActive: Boolean = true
        protected set

    fun isValidForUse(): Boolean {
        if (!isActive) return false
        if (expiresAt?.isBefore(Instant.now()) == true) return false
        return maxUsageCount == null || currentUsageCount < maxUsageCount!!
    }

    /**
     * Increments the usage counter for this promo code.
     * @return true if successful, false if limit was already reached.
     */
    fun redeem(): Boolean {
        if (!isValidForUse()) {
            return false
        }
        this.currentUsageCount++
        return true
    }

    fun deactivate() {
        this.isActive = false
    }
}

/**
 * Types of discounts supported by promo codes
 */
enum class DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT
}
