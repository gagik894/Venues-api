package app.venues.booking.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
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
@EntityListeners(AuditingEntityListener::class)
data class Cart(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var token: UUID = UUID.randomUUID(),

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(name = "guest_id")
    var guestId: Long? = null,

    @Column(name = "event_session_id", nullable = false)
    var sessionId: Long,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "last_activity_at", nullable = false)
    var lastActivityAt: Instant = Instant.now(),

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant? = null
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun extendExpiration(minutes: Long) {
        expiresAt = Instant.now().plusSeconds(minutes * 60)
        lastActivityAt = Instant.now()
    }

    fun touch() {
        lastActivityAt = Instant.now()
    }
}

