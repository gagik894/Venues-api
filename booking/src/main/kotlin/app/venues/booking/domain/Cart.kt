package app.venues.booking.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * A "root" entity representing a temporary shopping cart.
 *
 * @param sessionId The `EventSession.id` this cart is for.
 * @param expiresAt The time this cart's reservations expire.
 * @param token A unique token to identify the cart.
 * @param userId The `User.id` (customer) who owns this cart (if logged in).
 */
@Entity
@Table(
    name = "carts",
    indexes = [
        Index(name = "idx_cart_token", columnList = "token"),
        Index(name = "idx_cart_user_id", columnList = "user_id"),
        Index(name = "idx_cart_expires_at", columnList = "expires_at")
    ]
)
class Cart(
    @Column(name = "event_session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false, unique = true)
    var token: UUID = UUID.randomUUID(),

    @Column(name = "user_id")
    var userId: UUID? = null,

) : AbstractUuidEntity() {

    @Column(name = "last_activity_at", nullable = false)
    @Access(AccessType.FIELD)
    private var _lastActivityAt: Instant = Instant.now()

    val lastActivityAt: Instant
        get() = _lastActivityAt

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    /**
     * Extends the cart's expiration time and updates its activity.
     */
    fun extendExpiration(minutes: Long) {
        this.expiresAt = Instant.now().plusSeconds(minutes * 60)
        this._lastActivityAt = Instant.now()
    }

    /**
     * Updates the last activity time without extending expiration.
     */
    fun touch() {
        this._lastActivityAt = Instant.now()
    }
}