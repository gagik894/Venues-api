package app.venues.booking.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Temporary seat reservation.
 *
 * Prices are snapshotted at add-to-cart time and remain fixed even if
 * the template price changes later. Auto-expires after 15 minutes.
 */
@Entity
@Table(
    name = "cart_seats",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_cart_seat_session_seat", columnNames = ["session_id", "seat_id"])
    ],
    indexes = [
        Index(name = "idx_cart_seat_session_id", columnList = "session_id"),
        Index(name = "idx_cart_seat_token", columnList = "reservation_token"),
        Index(name = "idx_cart_seat_expires_at", columnList = "expires_at")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class CartSeat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long,

    @Column(name = "seat_id", nullable = false)
    var seatId: Long,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    @Column(name = "user_id")
    var userId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    var guest: Guest? = null,

    @Column(name = "reservation_token", nullable = false)
    var reservationToken: UUID,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
}

