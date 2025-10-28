package app.venues.user.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

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
@EntityListeners(AuditingEntityListener::class)
data class UserFcmToken(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /**
     * ID of the user who owns this FCM token.
     * Foreign key reference to users table.
     */
    @Column(name = "user_id", nullable = false)
    val userId: Long,

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

    /**
     * Timestamp when this token was registered.
     * Automatically managed by JPA Auditing.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /**
     * Timestamp when this token was last modified.
     * Automatically managed by JPA Auditing.
     */
    @LastModifiedDate
    @Column(nullable = false)
    var lastModifiedAt: Instant = Instant.now()

) {
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

