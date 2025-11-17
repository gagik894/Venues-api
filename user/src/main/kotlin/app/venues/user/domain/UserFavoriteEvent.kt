package app.venues.user.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.util.*

/**
 * Join entity representing a user's favorited event.
 * This is a cross-module relationship (User -> Event).
 *
 * @param user The user who favorited the event.
 * @param eventId The UUID of the event that was favorited.
 * @param notificationsEnabled Whether the user wants notifications for this event.
 */
@Entity
@Table(
    name = "user_favorite_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_event_favorite",
            columnNames = ["user_id", "event_id"]
        )
    ]
)
class UserFavoriteEvent(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "event_id", nullable = false)
    val eventId: UUID,

    @Column(nullable = false)
    var notificationsEnabled: Boolean = true,

    ) : AbstractLongEntity()