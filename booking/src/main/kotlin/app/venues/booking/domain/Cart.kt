package app.venues.booking.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

/**
 * Shopping cart session.
 *
 * Acts as a container for cart items with unified expiration.
 * All items in the cart expire together when the cart session expires.
 */
@Entity
@Table(
    name = "carts",
    indexes = [
        Index(name = "idx_cart_token", columnList = "token"),
        Index(name = "idx_cart_user", columnList = "user_id"),
        Index(name = "idx_cart_expires", columnList = "expires_at"),
        Index(name = "idx_cart_session", columnList = "event_session_id")
    ]
)
class Cart(
    @Column(nullable = false, unique = true)
    var token: UUID = UUID.randomUUID(),

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "guest_id")
    var guestId: UUID? = null,

    @Column(name = "event_session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "last_activity_at", nullable = false)
    var lastActivityAt: Instant = Instant.now(),
) : AbstractUuidEntity() {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun extendExpiration(minutes: Long) {
        expiresAt = Instant.now().plusSeconds(minutes * 60)
        lastActivityAt = Instant.now()
    }

    fun touch() {
        lastActivityAt = Instant.now()
    }
}

