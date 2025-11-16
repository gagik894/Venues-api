package app.venues.venue.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener

/**
 * Entity representing a user review and rating for a venue.
 *
 * Reviews help other users make informed decisions about venues.
 * Each user can only have one review per venue (can be updated).
 */
@Entity
@Table(
    name = "venue_reviews",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_review_user_venue",
            columnNames = ["venue_id", "user_id"]
        )
    ],
    indexes = [
        Index(name = "idx_venue_review_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_review_user_id", columnList = "user_id"),
        Index(name = "idx_venue_review_rating", columnList = "rating"),
        Index(name = "idx_venue_review_moderated", columnList = "is_moderated")
    ]
)
@EntityListeners(AuditingEntityListener::class)
class VenueReview(
    /**
     * The venue being reviewed
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * ID of the user who wrote this review
     * References the user from the user module
     */
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    /**
     * Rating from 1 to 5 stars
     */
    @Column(name = "rating", nullable = false)
    var rating: Int,

    /**
     * Optional written comment
     */
    @Column(name = "comment", columnDefinition = "TEXT")
    var comment: String? = null,

    /**
     * Whether this review has been moderated/approved
     * Used for content moderation in government applications
     */
    @Column(name = "is_moderated", nullable = false)
    var isModerated: Boolean = false,

    ) : AbstractUuidEntity() {
    init {
        // Validate rating is between 1 and 5
        require(rating in 1..5) { "Rating must be between 1 and 5" }
    }
}
