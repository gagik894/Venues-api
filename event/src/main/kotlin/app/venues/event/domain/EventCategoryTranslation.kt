package app.venues.event.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Translation for event category names.
 *
 * Allows categories to be displayed in multiple languages.
 */
@Entity
@Table(
    name = "event_category_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_category_translation_category_language",
            columnNames = ["category_id", "language"]
        )
    ],
    indexes = [
        Index(name = "idx_category_translation_category_id", columnList = "category_id"),
        Index(name = "idx_category_translation_language", columnList = "language")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class EventCategoryTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The category this translation belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    var category: EventCategory,

    /**
     * Language code (ISO 639-1: en, fr, es, hy, ru, etc.)
     */
    @Column(nullable = false, length = 10)
    var language: String,

    /**
     * Translated category name
     */
    @Column(nullable = false, length = 100)
    var name: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
)

