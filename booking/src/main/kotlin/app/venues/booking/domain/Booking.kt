package app.venues.booking.domain

import app.venues.event.domain.EventSession
import app.venues.user.domain.User
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Booking entity representing a finalized booking after checkout.
 *
 * Created in Phase 2 after cart is converted to booking.
 * Contains all booking details and payment information.
 */
@Entity
@Table(
    name = "bookings",
    indexes = [
        Index(name = "idx_booking_user_id", columnList = "user_id"),
        Index(name = "idx_booking_guest_id", columnList = "guest_id"),
        Index(name = "idx_booking_session_id", columnList = "session_id"),
        Index(name = "idx_booking_status", columnList = "status"),
        Index(name = "idx_booking_reservation_token", columnList = "reservation_token")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Booking(
    @Id
    @Column(columnDefinition = "UUID")
    var id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    var guest: Guest? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: EventSession,

    @Column(name = "reservation_token", unique = true, nullable = false, columnDefinition = "UUID")
    var reservationToken: UUID,

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
    var paymentId: String? = null,

    @OneToMany(mappedBy = "booking", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<BookingItem> = mutableListOf(),

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    fun isCancellable(): Boolean = status in setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)

    fun confirm(paymentId: String? = null) {
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

