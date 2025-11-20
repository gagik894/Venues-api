package app.venues.user.domain

//TODO: fully delete this
//
///**
// * Represents a user's instance of a promotional code.
// * This is a child entity of `User`.
// *
// * @param user The user this promo code belongs to.
// * @param promoCode The code string, which links to a `VenuePromoCode` definition.
// * @param discountType The type of discount (PERCENTAGE or FIXED_AMOUNT).
// * @param discountValue The value of the discount.
// * @param maxUses The maximum number of times this code can be used by the user.
// * @param expiresAt The expiration date/time of this promo code for the user.
// */
//@Entity
//@Table(
//    name = "user_promo_codes",
//    uniqueConstraints = [
//        UniqueConstraint(
//            name = "uk_user_promo_code",
//            columnNames = ["user_id", "promo_code"]
//        )
//    ]
//)
//class UserPromoCode(
//    @ManyToOne(fetch = FetchType.LAZY, optional = false)
//    @JoinColumn(name = "user_id", nullable = false)
//    val user: User,
//
//    @Column(name = "promo_code", nullable = false, length = 50)
//    val promoCode: String,
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "discount_type", nullable = false, length = 20)
//    val discountType: DiscountType,
//
//    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
//    val discountValue: BigDecimal,
//
//    @Column(name = "max_uses")
//    val maxUses: Int? = null,
//
//    @Column(name = "expires_at")
//    val expiresAt: Instant? = null,
//) : AbstractLongEntity() {
//
//    // --- Internal State (Encapsulated) ---
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "status", nullable = false, length = 20)
//    @Access(AccessType.FIELD)
//    var status: PromoCodeStatus = PromoCodeStatus.AVAILABLE
//        protected set
//
//    @Column(name = "times_used", nullable = false)
//    @Access(AccessType.FIELD)
//    var timesUsed: Int = 0
//        protected set
//
//    /**
//     * The `Booking.id` where this code was first/last used.
//     */
//    @Column(name = "used_in_booking_id")
//    @Access(AccessType.FIELD)
//    var usedInBookingId: UUID? = null
//        protected set
//
//    // --- Public Behaviors ---
//
//    fun isValid(): Boolean {
//        if (status != PromoCodeStatus.AVAILABLE) return false
//        if (expiresAt?.isBefore(Instant.now()) == true) return false
//        return maxUses == null || timesUsed < maxUses!!
//    }
//
//    /**
//     * Marks this promo code as used and updates its state.
//     *
//     * @param bookingId The ID of the booking where the code was applied.
//     */
//    fun markAsUsed(bookingId: UUID) {
//        this.timesUsed++
//        if (this.usedInBookingId == null) {
//            this.usedInBookingId = bookingId
//        }
//        if (maxUses != null && timesUsed >= maxUses!!) {
//            this.status = PromoCodeStatus.EXHAUSTED
//        } else {
//            this.status = PromoCodeStatus.USED
//        }
//    }
//
//    /**
//     * Calculates the discount for a given order total.
//     */
//    fun calculateDiscount(orderTotal: BigDecimal): BigDecimal {
//        if (!isValid()) return BigDecimal.ZERO
//
//        return when (discountType) {
//            DiscountType.PERCENTAGE -> orderTotal.multiply(discountValue.divide(BigDecimal(100)))
//            DiscountType.FIXED_AMOUNT -> discountValue.min(orderTotal) // Cannot discount more than total
//        }
//    }
//}
//
///**
// * Status of a promo code for a specific user.
// */
//enum class PromoCodeStatus {
//    /** Available for use */
//    AVAILABLE,
//
//    /** Has been used at least once (but may have more uses available) */
//    USED,
//
//    /** All uses exhausted */
//    EXHAUSTED,
//
//    /** Expired */
//    EXPIRED,
//
//    /** Revoked by admin */
//    REVOKED
//}
//
///**
// * Type of discount provided by a promo code.
// */
//enum class DiscountType {
//    /** Percentage discount (e.g., 10% off) */
//    PERCENTAGE,
//
//    /** Fixed amount discount (e.g., $5 off) */
//    FIXED_AMOUNT
//}
//
