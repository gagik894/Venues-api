package app.venues.seating.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener

/**
 * Translation for Level labels.
 *
 * Supports multi-language labels for sections/areas.
 */
@Entity
@Table(
    name = "level_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_level_translation_level_language",
            columnNames = ["level_id", "language"]
        )
    ],
    indexes = [
        Index(name = "idx_level_translation_level_id", columnList = "level_id"),
        Index(name = "idx_level_translation_language", columnList = "language")
    ]
)
@EntityListeners(AuditingEntityListener::class)
class LevelTranslation(
    /**
     * The level this translation belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    var level: Level,

    /**
     * Language code (ISO 639-1: en, hy, ru, etc.)
     */
    @Column(nullable = false, length = 10)
    var language: String,

    /**
     * Translated level label
     */
    @Column(name = "level_label", nullable = false, length = 255)
    var levelLabel: String,
) : AbstractLongEntity()