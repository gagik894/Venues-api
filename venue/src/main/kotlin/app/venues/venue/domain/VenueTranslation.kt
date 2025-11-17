package app.venues.venue.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * A translation for a Venue's text fields (e.g., name, description).
 * This is a child entity of Venue.
 *
 * @param venue The venue this translation belongs to.
 * @param language The language code (e.g., "en", "hy").
 * @param name The translated name.
 * @param description The translated description.
 */
@Entity
@Table(
    name = "venue_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_translation_venue_language",
            columnNames = ["venue_id", "language"]
        )
    ]
)
class VenueTranslation(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    @Column(name = "language", nullable = false, length = 10)
    var language: String,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    ) : AbstractLongEntity()