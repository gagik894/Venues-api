package app.venues.booking.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Temporary GA ticket reservation.
 *
 * Prices are snapshotted at add-to-cart time and remain fixed even if
 * the template price changes later. Auto-expires after 15 minutes.
 */
@Entity
@Table(
    name = "cart_items",
    indexes = [
        Index(name = "idx_cart_item_session_id", columnList = "session_id"),
        Index(name = "idx_cart_item_token", columnList = "reservation_token"),
        Index(name = "idx_cart_item_expires_at", columnList = "expires_at")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class CartItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long,

    @Column(name = "level_id", nullable = false)
    var levelId: Long,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    @Column(name = "user_id")
    var userId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    var guest: Guest? = null,

    @Column(name = "reservation_token", nullable = false)
    var reservationToken: UUID,

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
    fun getTotalPrice(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))
}

