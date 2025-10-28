package app.venues.event.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Event Category entity for organizing and filtering events.
 *
 * Categories are stored in the database with:
 * - Translated names for multi-language support
 * - Display order for sorting
 * - Color codes for UI display
 *
 * Examples: Theater, Concert, Opera, Ballet, Museum Exhibition, etc.
 */
@Entity
@Table(
    name = "event_categories",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_event_category_key", columnNames = ["category_key"])
    ],
    indexes = [
        Index(name = "idx_event_category_display_order", columnList = "display_order"),
        Index(name = "idx_event_category_active", columnList = "is_active")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class EventCategory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * Unique key for the category (e.g., "THEATER", "CONCERT")
     * Used for programmatic access and URL slugs
     */
    @Column(name = "category_key", nullable = false, unique = true, length = 50)
    var categoryKey: String,

    /**
     * Default name (English or primary language)
     */
    @Column(nullable = false, length = 100)
    var name: String,

    /**
     * Color code for UI display (hex: #FF5733)
     */
    @Column(length = 7)
    var color: String? = null,

    /**
     * Icon identifier (e.g., font-awesome icon name or URL)
     */
    @Column(length = 100)
    var icon: String? = null,

    /**
     * Display order for sorting (lower numbers appear first)
     */
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    /**
     * Whether this category is active and should be shown
     */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    /**
     * Translations for category name
     */
    @OneToMany(mappedBy = "category", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var translations: MutableList<EventCategoryTranslation> = mutableListOf(),

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    /**
     * Get translated name for a specific language
     */
    fun getTranslatedName(language: String): String {
        return translations.find { it.language == language }?.name ?: name
    }

    /**
     * Add a translation
     */
    fun addTranslation(translation: EventCategoryTranslation) {
        translations.add(translation)
        translation.category = this
    }
}

