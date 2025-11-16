package app.venues.venue.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Entity representing a translation of venue information in different languages.
 *
 * Supports multi-language venue names and descriptions for international accessibility.
 * This allows venues to provide information in multiple languages for diverse audiences.
 */
@Entity
@Table(
    name = "venue_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_translation_venue_language",
            columnNames = ["venue_id", "language"]
        )
    ],
    indexes = [
        Index(name = "idx_venue_translation_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_translation_language", columnList = "language")
    ]
)
class VenueTranslation(
    /**
     * The venue this translation belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * Language code (e.g., "en", "fr", "es", "hy", "ru")
     */
    @Column(name = "language", nullable = false, length = 10)
    var language: String,

    /**
     * Translated venue name
     */
    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    /**
     * Translated venue description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,
) : AbstractLongEntity()
