package app.venues.seating.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * A translation for a Level's text fields.
 * This is a child entity of Level.
 *
 * @param level The level this translation belongs to.
 * @param language The language code.
 * @param levelLabel The translated label.
 */
@Entity
@Table(
    name = "level_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_level_translation_level_language",
            columnNames = ["level_id", "language"]
        )
    ]
)
class LevelTranslation(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    var level: Level,

    @Column(nullable = false, length = 10)
    var language: String,

    @Column(name = "level_label", nullable = false, length = 255)
    var levelLabel: String,

    ) : AbstractLongEntity()