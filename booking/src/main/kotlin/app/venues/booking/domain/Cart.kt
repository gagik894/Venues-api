package app.venues.booking.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Represents a temporary shopping cart session.
 *
 * @param sessionId The [UUID] of the EventSession.
 * @param expiresAt The absolute timestamp when the cart reservations expire.
 * @param token A unique public token for API access.
 * @param userId The [UUID] of the authenticated user (nullable).
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

    @Column(name = "token", nullable = false, unique = true)
    var token: UUID = UUID.randomUUID(),

    @Column(name = "user_id")
    var userId: UUID? = null,

) : AbstractUuidEntity() {

    @Column(name = "last_activity_at", nullable = false)
    @Access(AccessType.FIELD)
    var lastActivityAt: Instant = Instant.now()
        protected set

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    /**
     * Extends the cart's expiration time and updates its activity.
     */
    fun extendExpiration(minutes: Long) {
        this.expiresAt = Instant.now().plusSeconds(minutes * 60)
        this.lastActivityAt = Instant.now()
    }

    /**
     * Updates the last activity time without extending expiration.
     */
    fun touch() {
        this.lastActivityAt = Instant.now()
    }
}
