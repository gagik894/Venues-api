package app.venues.venue.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * A translation for a Venue's text fields (e.g., name, description).
 * This is a child entity of Venue.
 *
 * @param venue The venue this translation belongs to.
 * @param language The language code (e.g., "en", "hy").
 * @param name The translated name.
 * @param description The translated description.
 * @param address The translated address.
 */
@Entity
@Table(
    name = "venue_translations", uniqueConstraints = [
        UniqueConstraint(name = "uq_venue_lang", columnNames = ["venue_id", "language"])
    ]
)
class VenueTranslation(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    @Column(name = "language", nullable = false, length = 10)
    var language: String,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "address", length = 500)
    var address: String? = null

) : AbstractLongEntity()