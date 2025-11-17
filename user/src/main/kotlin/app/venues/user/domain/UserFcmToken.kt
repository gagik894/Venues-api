package app.venues.user.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Represents a single Firebase Cloud Messaging (FCM) token for a user's device.
 * A user can have multiple tokens.
 *
 * @param user The user who this token belongs to.
 * @param token The unique FCM token string.
 * @param platform The device platform (e.g., "android", "ios", "web").
 * @param deviceName Optional human-readable name for the device.
 */
@Entity
@Table(
    name = "user_fcm_tokens",
    indexes = [
        Index(name = "idx_fcm_user_id", columnList = "user_id"),
        Index(name = "idx_fcm_token", columnList = "token", unique = true)
    ]
)
class UserFcmToken(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, unique = true, length = 512)
    val token: String,

    @Column(nullable = false, length = 20)
    val platform: String,

    @Column(length = 100)
    var deviceName: String? = null,

    ) : AbstractLongEntity() {

    @Column
    @Access(AccessType.FIELD)
    private var _lastUsedAt: Instant? = null

    /**
     * Public, read-only view of the last used time.
     */
    val lastUsedAt: Instant?
        get() = _lastUsedAt

    /**
     * Checks if this token is stale and should be considered for deletion.
     * @return true if the token hasn't been used in 90 days.
     */
    fun isStale(staleDurationInDays: Long = 90): Boolean {
        val lastUsed = _lastUsedAt ?: createdAt
        val staleThreshold = Instant.now().minusSeconds(staleDurationInDays * 24 * 60 * 60)
        return lastUsed.isBefore(staleThreshold)
    }

    /**
     * Updates the last used timestamp to now.
     */
    fun markAsUsed() {
        this._lastUsedAt = Instant.now()
    }
}
