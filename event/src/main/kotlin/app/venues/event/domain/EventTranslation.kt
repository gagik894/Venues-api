package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * A translation for an Event's text fields.
 * This is a child entity of Event.
 *
 * @param event The event this translation belongs to.
 * @param language The language code.
 * @param title The translated title.
 * @param description The translated description.
 */
@Entity
@Table(
    name = "event_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_translation_event_language",
            columnNames = ["event_id", "language"]
        )
    ]
)
class EventTranslation(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,

    @Column(name = "language", nullable = false, length = 10)
    var language: String,

    @Column(name = "title", nullable = false, length = 255)
    var title: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    ) : AbstractLongEntity()