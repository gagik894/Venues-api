package app.venues.booking.domain

import app.venues.booking.api.domain.BookingStatus
import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.*

/**
 * A "root" entity representing a finalized booking.
 *
 * This entity serves as the aggregate root for the booking transaction.
 * It is created after a Cart is successfully checked out.
 *
 * @param userId The [UUID] of the customer who made this booking (nullable for guest checkout).
 * @param guest The [Guest] entity for this booking (nullable for authenticated users).
 * @param sessionId The [UUID] of the `EventSession` this booking applies to.
 * @param totalPrice The total monetary value of the booking.
 * @param currency The 3-letter ISO currency code (default "AMD").
 * @param salesChannel The channel through which this booking was created (WEBSITE, DIRECT_SALE, PLATFORM).
 * @param platformId The [UUID] of the external platform initiating the booking (only for PLATFORM sales).
 * @param staffId The [UUID] of the staff member who created this booking (only for DIRECT_SALE).
 * @param venueId The [UUID] of the venue (denormalized for faster reporting).
 * @param externalOrderNumber An optional reference number from an external system.
 */
@Entity
@Table(
    name = "bookings",
    indexes = [
        Index(name = "idx_booking_user_id", columnList = "user_id"),
        Index(name = "idx_booking_guest_id", columnList = "guest_id"),
        Index(name = "idx_booking_session_id", columnList = "session_id"),
        Index(name = "idx_booking_platform_id", columnList = "platform_id"),
        Index(name = "idx_booking_venue_id", columnList = "venue_id"),
        Index(name = "idx_booking_sales_channel", columnList = "sales_channel"),
        Index(name = "idx_booking_staff_id", columnList = "staff_id"),
        Index(name = "idx_booking_channel_status", columnList = "sales_channel, status")
    ]
)
class Booking(
    @Column(name = "user_id")
    var userId: UUID?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    var guest: Guest?,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    var totalPrice: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "AMD",

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_channel", nullable = false, length = 20)
    var salesChannel: SalesChannel,

    @Column(name = "platform_id")
    var platformId: UUID?,

    @Column(name = "staff_id")
    var staffId: UUID?,

    @Column(name = "venue_id")
    var venueId: UUID?,

    @Column(name = "external_order_number", length = 100, unique = true)
    var externalOrderNumber: String? = null,

    @Column(name = "service_fee_amount", nullable = false, precision = 10, scale = 2)
    var serviceFeeAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    var discountAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "promo_code", length = 50)
    var promoCode: String? = null,

    @Version
    @Column(name = "version")
    var version: Long = 0

) : AbstractUuidEntity() {

    init {
        require(serviceFeeAmount.signum() >= 0) { "serviceFeeAmount must be >= 0" }
        require(discountAmount.signum() >= 0) { "discountAmount must be >= 0" }

        // Validate sales channel constraints
        when (salesChannel) {
            SalesChannel.PLATFORM -> {
                require(platformId != null) {
                    "PLATFORM sales must have platformId set"
                }
            }

            SalesChannel.DIRECT_SALE -> {
                require(staffId != null) {
                    "DIRECT_SALE must have staffId set to track which staff member made the sale"
                }
            }

            SalesChannel.WEBSITE -> {
                // No additional constraints for website sales
            }
        }
    }

    // --- Internal State (Encapsulated) ---
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    var status: BookingStatus = BookingStatus.PENDING
        protected set

    @Column(name = "confirmed_at")
    @Access(AccessType.FIELD)
    var confirmedAt: Instant? = null
        protected set

    @Column(name = "cancelled_at")
    @Access(AccessType.FIELD)
    var cancelledAt: Instant? = null
        protected set

    @Column(name = "cancellation_reason", length = 500)
    @Access(AccessType.FIELD)
    var cancellationReason: String? = null
        protected set

    @Column(name = "payment_id")
    @Access(AccessType.FIELD)
    var paymentId: UUID? = null
        protected set

    // --- Relationships ---
    @OneToMany(mappedBy = "booking", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<BookingItem> = mutableListOf()

    // --- Public Behaviors ---
    fun isCancellable(): Boolean {
        return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED
    }

    /**
     * Apply a service fee given as a percentage of a provided base amount.
     * Percent is expected as a decimal percentage (e.g. 2.5 for 2.5\%).
     * Updates `serviceFeeAmount` and `totalPrice` (baseAmount + fee).
     *
     * @throws IllegalArgumentException when percent is negative or baseAmount is negative.
     */
    fun applyServiceFeePercent(baseAmount: BigDecimal, percent: BigDecimal) {
        require(percent.signum() >= 0) { "percent must be >= 0" }
        require(baseAmount.signum() >= 0) { "baseAmount must be >= 0" }

        val fee = baseAmount.multiply(percent)
            .divide(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)

        serviceFeeAmount = fee
        totalPrice = baseAmount.add(serviceFeeAmount)
    }

    /**
     * Confirms the booking, moving it to a confirmed state
     * and recording the payment.
     *
     * @param paymentId The unique identifier for the payment transaction (internal UUID).
     * @throws IllegalStateException if the booking is not in a PENDING state.
     */
    fun confirm(paymentId: UUID?) {
        if (this.status != BookingStatus.PENDING) {
            throw IllegalStateException("Booking $id cannot be confirmed (status is ${status}).")
        }
        this.status = BookingStatus.CONFIRMED
        this.confirmedAt = Instant.now()
        this.paymentId = paymentId
    }

    /**
     * Cancels the booking and records a reason.
     *
     * @param reason A reason for the cancellation (e.g., "User request", "Payment failed").
     */
    fun cancel(reason: String?) {
        if (this.status == BookingStatus.CANCELLED) {
            return // Already cancelled
        }
        this.status = BookingStatus.CANCELLED
        this.cancelledAt = Instant.now()
        this.cancellationReason = reason
    }

    /**
     * Adds a [BookingItem] to this booking.
     * Also sets the item's `booking` reference to this booking.
     * @param item The [BookingItem] to add.
     */
    fun addItem(item: BookingItem) {
        items.add(item)
        item.booking = this
    }
}
