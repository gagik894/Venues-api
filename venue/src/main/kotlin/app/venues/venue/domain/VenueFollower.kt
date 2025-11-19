package app.venues.venue.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*
import java.util.*

/**
 * Join entity linking a `User` (customer) to a `Venue` they follow.
 * This is a cross-module relationship.
 *
 * @param venue The venue being followed.
 * @param userId The ID of the user who is following.
 * @param notificationsEnabled Whether the user has enabled notifications for this venue.
 */
@Entity
@Table(
    name = "venue_followers",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_follower_user_venue",
            columnNames = ["venue_id", "user_id"]
        )
    ]
)
class VenueFollower(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * The `User.id` (customer) who is following.
     */
    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "notifications_enabled", nullable = false)
    var notificationsEnabled: Boolean = true,

    ) : AbstractLongEntity()
