package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * A translation for an EventCategory's text fields.
 * This is a child entity of EventCategory.
 *
 * @param category The category this translation belongs to.
 * @param language The language code.
 * @param name The translated name.
 */
@Entity
@Table(
    name = "event_category_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_category_translation_category_language",
            columnNames = ["category_id", "language"]
        )
    ]
)
class EventCategoryTranslation(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    var category: EventCategory,

    @Column(name = "language", nullable = false, length = 10)
    var language: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    ) : AbstractLongEntity()