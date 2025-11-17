package app.venues.booking.domain

import app.venues.booking.api.domain.BookingStatus
import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal
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
 * @param platformId The [UUID] of the external platform initiating the booking (if applicable).
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
        Index(name = "idx_booking_venue_id", columnList = "venue_id")
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

    @Column(nullable = false, length = 3)
    var currency: String = "AMD",

    @Column(name = "platform_id")
    var platformId: UUID?,

    @Column(name = "venue_id")
    var venueId: UUID?,

    @Column(name = "external_order_number", length = 100, unique = true)
    var externalOrderNumber: String? = null

) : AbstractUuidEntity() {

    // --- Internal State (Encapsulated) ---
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    private var _status: BookingStatus = BookingStatus.PENDING

    val status: BookingStatus
        get() = _status

    @Column(name = "confirmed_at")
    @Access(AccessType.FIELD)
    private var _confirmedAt: Instant? = null

    val confirmedAt: Instant?
        get() = _confirmedAt

    @Column(name = "cancelled_at")
    @Access(AccessType.FIELD)
    private var _cancelledAt: Instant? = null

    val cancelledAt: Instant?
        get() = _cancelledAt

    @Column(name = "cancellation_reason", length = 500)
    @Access(AccessType.FIELD)
    private var _cancellationReason: String? = null

    val cancellationReason: String?
        get() = _cancellationReason

    @Column(name = "payment_id")
    @Access(AccessType.FIELD)
    private var _paymentId: UUID? = null

    val paymentId: UUID?
        get() = _paymentId

    // --- Relationships ---
    @OneToMany(mappedBy = "booking", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<BookingItem> = mutableListOf()

    // --- Public Behaviors ---
    fun isCancellable(): Boolean {
        return _status == BookingStatus.PENDING || _status == BookingStatus.CONFIRMED
    }

    /**
     * Confirms the booking, moving it to a confirmed state
     * and recording the payment.
     *
     * @param paymentId The unique identifier for the payment transaction.
     * @throws IllegalStateException if the booking is not in a PENDING state.
     */
    fun confirm(paymentId: UUID) {
        if (this._status != BookingStatus.PENDING) {
            throw IllegalStateException("Booking $id cannot be confirmed (status is $_status).")
        }
        this._status = BookingStatus.CONFIRMED
        this._confirmedAt = Instant.now()
        this._paymentId = paymentId
    }

    /**
     * Cancels the booking and records a reason.
     *
     * @param reason A reason for the cancellation (e.g., "User request", "Payment failed").
     */
    fun cancel(reason: String?) {
        if (this._status == BookingStatus.CANCELLED) {
            return // Already cancelled
        }
        this._status = BookingStatus.CANCELLED
        this._cancelledAt = Instant.now()
        this._cancellationReason = reason
    }

    fun addItem(item: BookingItem) {
        items.add(item)
        item.booking = this
    }
}