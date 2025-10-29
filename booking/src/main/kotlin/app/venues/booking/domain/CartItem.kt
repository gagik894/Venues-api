package app.venues.booking.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.*

/**
 * Cart item entity - temporary GA ticket hold.
 *
 * Created when user selects GA tickets.
 * Auto-expires after 15 minutes if not converted to booking.
 *
 * Cross-module relationships:
 * - sessionId references event module
 * - levelId references seating module
 * - userId references user module
 * - guest references booking module (same module)
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

    /**
     * Session ID - references event module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "session_id", nullable = false)
    var sessionId: Long,

    /**
     * Level ID - references seating module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "level_id", nullable = false)
    var levelId: Long,

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

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
}

