package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Venue Review Entity
 *
 * Represents user reviews and ratings for a venue.
 * Users can rate venues from 1 to 5 stars and leave comments.
 *
 * Features:
 * - One review per user per venue (enforced by unique constraint)
 * - Rating from 1 to 5
 * - Optional text comment
 * - Moderation support (future)
 */
@Entity
@Table(
    name = "venue_reviews",
    indexes = [
        Index(name = "idx_venue_review_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_review_user_id", columnList = "user_id"),
        Index(name = "idx_venue_review_rating", columnList = "rating")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_venue_review_user_venue", columnNames = ["venue_id", "user_id"])
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenueReview(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * ID of the user who wrote this review
     * Stored as Long to avoid coupling with User module
     */
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    /**
     * Rating from 1 to 5
     */
    @Column(nullable = false)
    var rating: Int,

    /**
     * Optional review comment
     */
    @Column(columnDefinition = "TEXT")
    var comment: String? = null,

    /**
     * Flag for moderation (if review is inappropriate)
     */
    @Column(nullable = false)
    var isModerated: Boolean = false,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    init {
        require(rating in 1..5) { "Rating must be between 1 and 5" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VenueReview

        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "VenueReview(id=$id, userId=$userId, rating=$rating)"
    }
}

