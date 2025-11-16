package app.venues.venue.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.util.*

/**
 * Entity representing a user following a venue.
 *
 * When users follow venues, they can receive notifications about new events,
 * updates, promotions, etc. This creates engagement and helps venues build
 * an audience.
 */
@Entity
@Table(
    name = "venue_followers",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_follower_user_venue",
            columnNames = ["venue_id", "user_id"]
        )
    ],
    indexes = [
        Index(name = "idx_venue_follower_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_follower_user_id", columnList = "user_id"),
        Index(name = "idx_venue_follower_notifications", columnList = "notifications_enabled")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenueFollower(
    /**
     * The venue being followed
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * ID of the user following this venue
     * References the user from the user module
     */
    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    /**
     * Whether the user wants to receive notifications from this venue
     */
    @Column(name = "notifications_enabled", nullable = false)
    var notificationsEnabled: Boolean = true,
) : AbstractLongEntity()