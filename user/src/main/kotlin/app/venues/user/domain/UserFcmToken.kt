package app.venues.user.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

/**
 * Entity representing a Firebase Cloud Messaging (FCM) token for push notifications.
 *
 * A user can have multiple FCM tokens (one per device/platform).
 * This allows sending push notifications to all user's devices.
 *
 * Design:
 * - One user can have many tokens (one per device)
 * - Tokens are unique to prevent duplicates
 * - Tracks platform (Android, iOS, Web) for targeted notifications
 * - Includes last used timestamp for token cleanup
 *
 * Best Practices:
 * - Tokens should be refreshed periodically
 * - Remove stale tokens (not used for 90+ days)
 * - Handle token expiration gracefully
 */
@Entity
@Table(
    name = "user_fcm_tokens",
    indexes = [
        Index(name = "idx_fcm_user_id", columnList = "user_id"),
        Index(name = "idx_fcm_token", columnList = "token", unique = true)
    ]
)
data class UserFcmToken(
    /**
     * ID of the user who owns this FCM token.
     * Foreign key reference to users table.
     */
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /**
     * The FCM token string provided by Firebase SDK.
     * Unique across all users and devices.
     */
    @Column(nullable = false, unique = true, length = 512)
    val token: String,

    /**
     * Platform/device type for this token.
     * Examples: "android", "ios", "web"
     * Used for platform-specific notifications.
     */
    @Column(nullable = false, length = 20)
    val platform: String,

    /**
     * Optional device identifier or name.
     * Helps users manage their devices (e.g., "John's iPhone", "Work Laptop").
     */
    @Column(length = 100)
    var deviceName: String? = null,

    /**
     * Timestamp when this token was last used successfully.
     * Updated when a notification is successfully delivered.
     * Used to identify and remove stale tokens.
     */
    @Column
    var lastUsedAt: Instant? = null,

    ) : AbstractLongEntity() {
    /**
     * Checks if this token is stale and should be removed.
     * Tokens not used for 90+ days are considered stale.
     *
     * @return true if token hasn't been used in 90 days
     */
    fun isStale(): Boolean {
        val lastUsed = lastUsedAt ?: createdAt
        val ninetyDaysAgo = Instant.now().minusSeconds(90 * 24 * 60 * 60)
        return lastUsed.isBefore(ninetyDaysAgo)
    }

    /**
     * Marks this token as recently used.
     * Updates the lastUsedAt timestamp to now.
     */
    fun markAsUsed() {
        lastUsedAt = Instant.now()
    }
}

