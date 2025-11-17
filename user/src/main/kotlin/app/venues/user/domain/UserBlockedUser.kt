package app.venues.user.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Join entity representing a user blocking another user.
 * This is an intra-module relationship.
 *
 * @param blockingUser The user who initiated the block.
 * @param blockedUser The user who is being blocked.
 */
@Entity
@Table(
    name = "user_blocked_users",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_blocking_blocked_user",
            columnNames = ["blocking_user_id", "blocked_user_id"]
        )
    ]
)
class UserBlockedUser(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocking_user_id", nullable = false)
    val blockingUser: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    val blockedUser: User,

    @Column(length = 50)
    var blockReason: String? = null,

    ) : AbstractLongEntity() {
    init {
        require(blockingUser.id != blockedUser.id) {
            "A user cannot block themselves"
        }
    }
}