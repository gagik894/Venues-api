package app.venues.booking.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.*

/**
 * Cart seat entity - temporary seat hold.
 *
 * Created immediately when user clicks a seat.
 * Auto-expires after 15 minutes if not converted to booking.
 *
 * Cross-module relationships:
 * - sessionId references event module
 * - seatId references seating module
 * - userId references user module
 * - guest references booking module (same module)
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

    /**
     * Session ID - references event module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "session_id", nullable = false)
    var sessionId: Long,

    /**
     * Seat ID - references seating module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "seat_id", nullable = false)
    var seatId: Long,

    /**
     * User ID - references user module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "user_id")
    var userId: Long? = null,

    /**
     * Guest - references booking module (same module)
     * Can be null for logged-in users
     */
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

