package app.venues.venue.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*
import java.util.*

/**
 * A photo belonging to a venue.
 *
 * @param venue The venue this photo is for.
 * @param userId The `User.id` (customer) who uploaded it.
 * @param url The URL of the image.
 * @param caption An optional caption for the photo.
 * @param displayOrder The order in which the photo should be displayed.
 */
@Entity
@Table(name = "venue_photos")
class VenuePhoto(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "url", nullable = false, length = 500)
    var url: String,

    @Column(name = "caption", length = 500)
    var caption: String? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
) : AbstractLongEntity()
