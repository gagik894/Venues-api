package app.venues.user.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.util.*

/**
 * Entity representing a user's favorite/bookmarked event.
 *
 * This is a join table between users and events.
 * Allows users to save events they're interested in for quick access.
 *
 * Design:
 * - Many-to-many relationship between User and Event
 * - Tracks when event was favorited
 * - Unique constraint prevents duplicate favorites
 * - Can be extended with notification preferences
 *
 * Use Cases:
 * - User favorites an event for later viewing
 * - User receives notifications about favorited events
 * - User views their list of favorite events
 * - Analytics on popular events
 */
@Entity
@Table(
    name = "user_favorite_events",
    indexes = [
        Index(name = "idx_favorite_user_id", columnList = "user_id"),
        Index(name = "idx_favorite_event_id", columnList = "event_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_event_favorite",
            columnNames = ["user_id", "event_id"]
        )
    ]
)
class UserFavoriteEvent(
    /**
     * The user who favorited the event.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    /**
     * ID of the favorited event.
     * Foreign key reference to events table (in event module).
     */
    @Column(name = "event_id", nullable = false)
    val eventId: UUID,

    /**
     * Whether user wants to receive notifications about this event.
     * Examples: reminders before event starts, cancellation notices, etc.
     */
    @Column(nullable = false)
    var notificationsEnabled: Boolean = true,

    /**
     * Optional note/reminder about why this event was favorited.
     * User can add personal notes.
     */
    @Column(length = 500)
    var userNote: String? = null,

    ) : AbstractLongEntity() {
    /**
     * Creates a copy with notifications toggled.
     *
     * @param enabled New notification state
     * @return Updated favorite event
     */
    fun withNotifications(enabled: Boolean): UserFavoriteEvent {
        return this.apply {
            this.notificationsEnabled = enabled
        }
    }
}

