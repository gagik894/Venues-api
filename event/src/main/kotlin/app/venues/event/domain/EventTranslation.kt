package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Translation for event title and description.
 *
 * Supports multi-language event information for international audiences.
 */
@Entity
@Table(
    name = "event_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_translation_event_language",
            columnNames = ["event_id", "language"]
        )
    ],
    indexes = [
        Index(name = "idx_event_translation_event_id", columnList = "event_id"),
        Index(name = "idx_event_translation_language", columnList = "language")
    ]
)
class EventTranslation(
    /**
     * The event this translation belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,

    /**
     * Language code (ISO 639-1: en, fr, es, hy, ru, etc.)
     */
    @Column(nullable = false, length = 10)
    var language: String,

    /**
     * Translated title
     */
    @Column(nullable = false, length = 255)
    var title: String,

    /**
     * Translated description
     */
    @Column(columnDefinition = "TEXT")
    var description: String? = null,
) : AbstractLongEntity()

