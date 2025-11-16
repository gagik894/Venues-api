package app.venues.booking.domain

import app.venues.booking.api.domain.BookingStatus
import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Booking entity representing a finalized booking after checkout.
 *
 * Created in Phase 2 after cart is converted to booking.
 * Contains all booking details and payment information.
 *
 * Cross-module relationships:
 * - userId references user module
 * - guestId references booking module (same module)
 * - sessionId references event module
 */
@Entity
@Table(
    name = "bookings",
    indexes = [
        Index(name = "idx_booking_user_id", columnList = "user_id"),
        Index(name = "idx_booking_guest_id", columnList = "guest_id"),
        Index(name = "idx_booking_session_id", columnList = "session_id"),
        Index(name = "idx_booking_reservation_token", columnList = "reservation_token"),
        Index(name = "idx_booking_platform_id", columnList = "platform_id"),
        Index(name = "idx_booking_venue_id", columnList = "venue_id")
    ]
)
class Booking(
    /**
     * User ID - references user module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "user_id")
    var userId: UUID? = null,

    /**
     * Guest ID - references Guest entity in booking module
     * Can be null for logged-in users
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    var guest: Guest? = null,

    /**
     * Session ID - references event module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    /**
     * Reserved platform's order number for cross-referencing
     */
    @Column(name = "external_order_number", length = 100, unique = true)
    var externalOrderNumber: String? = null,

    /**
     * Platform ID if booking was made through external platform integration
     */
    @Column(name = "platform_id")
    var platformId: UUID? = null,

    /**
     * Venue ID - the venue where the event is held
     * Denormalized for reporting and analytics
     */
    @Column(name = "venue_id")
    var venueId: UUID? = null,

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    var totalPrice: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String = "AMD",

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: BookingStatus = BookingStatus.PENDING,

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "cancellation_reason", length = 500)
    var cancellationReason: String? = null,

    @Column(name = "payment_id", length = 100)
    var paymentId: UUID? = null,

    @OneToMany(mappedBy = "booking", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<BookingItem> = mutableListOf(),
) : AbstractUuidEntity() {
    fun isCancellable(): Boolean = status in setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)

    //TODO introduce separate Payment entity to track payment details and status
    fun confirm(paymentId: UUID? = null) {
        status = BookingStatus.CONFIRMED
        confirmedAt = Instant.now()
        this.paymentId = paymentId
    }

    fun cancel(reason: String? = null) {
        status = BookingStatus.CANCELLED
        cancelledAt = Instant.now()
        cancellationReason = reason
    }

    fun addItem(item: BookingItem) {
        items.add(item)
        item.booking = this
    }
}

