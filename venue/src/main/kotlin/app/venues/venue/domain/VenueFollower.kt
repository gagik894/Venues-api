package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Venue Follower Entity
 *
 * Represents the many-to-many relationship between users and venues.
 * Users can follow venues to receive updates about new events.
 *
 * Features:
 * - Track when user started following
 * - Support for notifications preferences (future)
 */
@Entity
@Table(
    name = "venue_followers",
    indexes = [
        Index(name = "idx_venue_follower_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_follower_user_id", columnList = "user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_venue_follower_user_venue", columnNames = ["venue_id", "user_id"])
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenueFollower(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * ID of the user who is following this venue
     * Stored as Long to avoid coupling with User module
     */
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    /**
     * Flag indicating if user wants to receive notifications
     */
    @Column(nullable = false)
    var notificationsEnabled: Boolean = true,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VenueFollower

        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "VenueFollower(id=$id, userId=$userId, notificationsEnabled=$notificationsEnabled)"
    }
}

