package app.venues.venue.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import java.util.*

/**
 * A review for a Venue, written by a User (customer).
 * This is a child entity, owned by the Venue.
 *
 * @param venue The venue being reviewed.
 * @param userId The `User.id` (customer) who wrote the review.
 * @param rating The star rating from 1 to 5.
 * @param comment An optional text comment.
 */
@Entity
@Table(
    name = "venue_reviews",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_review_user_venue",
            columnNames = ["venue_id", "user_id"]
        )
    ]
)
class VenueReview(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "rating", nullable = false)
    var rating: Int,

    @Column(name = "comment", columnDefinition = "TEXT")
    var comment: String? = null,

    ) : AbstractLongEntity() {

    @Column(name = "is_moderated", nullable = false)
    @Access(AccessType.FIELD)
    private var _isModerated: Boolean = false

    val isModerated: Boolean
        get() = _isModerated

    init {
        require(rating in 1..5) { "Rating must be between 1 and 5" }
    }

    fun approve() {
        this._isModerated = true
    }
}