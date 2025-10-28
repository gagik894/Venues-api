package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Entity representing a photo uploaded for a venue.
 *
 * Photos can be uploaded by venue owners or users who have visited the venue.
 * This allows for crowd-sourced venue imagery while maintaining attribution.
 */
@Entity
@Table(
    name = "venue_photos",
    indexes = [
        Index(name = "idx_venue_photo_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_photo_user_id", columnList = "user_id"),
        Index(name = "idx_venue_photo_display_order", columnList = "venue_id, display_order")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenuePhoto(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The venue this photo belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * ID of the user who uploaded this photo
     * References the user from the user module
     */
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    /**
     * URL/path to the photo file
     */
    @Column(name = "url", nullable = false, length = 500)
    var url: String,

    /**
     * Optional caption for the photo
     */
    @Column(name = "caption", length = 500)
    var caption: String? = null,

    /**
     * Display order for sorting photos (lower numbers first)
     */
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    // ===========================================
    // Audit Fields
    // ===========================================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
