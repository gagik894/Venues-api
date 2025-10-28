package app.venues.user.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

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
@EntityListeners(AuditingEntityListener::class)
data class UserFavoriteEvent(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /**
     * ID of the user who favorited the event.
     * Foreign key reference to users table.
     */
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    /**
     * ID of the favorited event.
     * Foreign key reference to events table (in event module).
     */
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

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

    /**
     * Timestamp when this event was added to favorites.
     * Automatically managed by JPA Auditing.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

) {
    /**
     * Creates a copy with notifications toggled.
     *
     * @param enabled New notification state
     * @return Updated favorite event
     */
    fun withNotifications(enabled: Boolean): UserFavoriteEvent {
        return copy(notificationsEnabled = enabled)
    }
}

