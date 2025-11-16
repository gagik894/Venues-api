package app.venues.user.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.util.*

/**
 * Entity representing a user blocking another user.
 *
 * This implements user privacy/safety features.
 * When a user blocks another user, they won't see each other's content/activity.
 *
 * Design:
 * - One-directional blocking (A blocks B doesn't mean B blocks A)
 * - Unique constraint prevents duplicate blocks
 * - Tracks reason and timestamp for moderation/analytics
 * - Can be extended with expiration/unblock features
 *
 * Use Cases:
 * - User blocks another user to hide their content
 * - Prevent blocked users from messaging/interacting
 * - Moderation and abuse reporting
 * - User privacy controls
 */
@Entity
@Table(
    name = "user_blocked_users",
    indexes = [
        Index(name = "idx_blocking_user_id", columnList = "blocking_user_id"),
        Index(name = "idx_blocked_user_id", columnList = "blocked_user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_blocking_blocked_user",
            columnNames = ["blocking_user_id", "blocked_user_id"]
        )
    ]
)
class UserBlockedUser(
    /**
     * ID of the user who initiated the block.
     * Foreign key reference to users table.
     */
    @Column(name = "blocking_user_id", nullable = false)
    val blockingUserId: UUID,

    /**
     * ID of the user who is being blocked.
     * Foreign key reference to users table.
     */
    @Column(name = "blocked_user_id", nullable = false)
    val blockedUserId: UUID,

    /**
     * Optional reason for blocking.
     * Examples: "spam", "harassment", "inappropriate_content"
     * Used for moderation and analytics.
     */
    @Column(length = 50)
    var blockReason: String? = null,

    /**
     * Optional additional details about the block.
     * Free-text explanation from the user.
     */
    @Column(length = 500)
    var blockReasonDetails: String? = null,
) : AbstractLongEntity() {
    init {
        require(blockingUserId != blockedUserId) {
            "A user cannot block themselves"
        }
    }

    /**
     * Checks if this block is related to abuse/harassment.
     *
     * @return true if block reason indicates abuse
     */
    fun isAbuseRelated(): Boolean {
        return blockReason in listOf("harassment", "abuse", "threats", "inappropriate_content")
    }
}

