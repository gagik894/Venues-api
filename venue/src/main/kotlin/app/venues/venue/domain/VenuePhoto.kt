package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Venue Photo Entity
 *
 * Represents photos uploaded by users or venue owners.
 * Can be associated with a user (uploader) and the venue.
 *
 * Features:
 * - Track who uploaded the photo
 * - Track when it was uploaded
 * - Support for moderation (future)
 */
@Entity
@Table(
    name = "venue_photos",
    indexes = [
        Index(name = "idx_venue_photo_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_photo_user_id", columnList = "user_id")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenuePhoto(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * ID of the user who uploaded this photo
     * Stored as Long to avoid coupling with User module
     */
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    /**
     * URL of the photo (stored in cloud storage)
     */
    @Column(nullable = false, length = 500)
    var url: String,

    /**
     * Optional caption or description
     */
    @Column(length = 500)
    var caption: String? = null,

    /**
     * Display order (lower numbers appear first)
     */
    @Column(nullable = false)
    var displayOrder: Int = 0,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VenuePhoto

        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "VenuePhoto(id=$id, userId=$userId, url='$url')"
    }
}

