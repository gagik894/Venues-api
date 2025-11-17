package app.venues.event.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * An internal definition for an event category.
 * This is a "child" or "internal" entity (uses AbstractLongEntity).
 * Its public-facing ID is the `categoryKey`.
 *
 * @param categoryKey The unique, human-readable key (e.g., "THEATER").
 * @param name The display name (e.g., "Theater").
 * @param color An optional color code (e.g., "#FF5733") for UI representation.
 * @param icon An optional icon name or URL for UI representation.
 * @param displayOrder The order in which this category should be displayed.
 */
@Entity
@Table(
    name = "event_categories",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_event_category_key", columnNames = ["category_key"])
    ]
)
class EventCategory(
    @Column(name = "category_key", nullable = false, unique = true, length = 50)
    var categoryKey: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "color", length = 7)
    var color: String? = null,

    @Column(name = "icon", length = 100)
    var icon: String? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    ) : AbstractLongEntity() {

    @Column(name = "is_active", nullable = false)
    @Access(AccessType.FIELD)
    private var _isActive: Boolean = true

    val isActive: Boolean
        get() = _isActive

    @OneToMany(mappedBy = "category", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var translations: MutableList<EventCategoryTranslation> = mutableListOf()

    fun getTranslatedName(language: String): String {
        return translations.find { it.language == language }?.name ?: name
    }

    fun activate() {
        this._isActive = true
    }

    fun deactivate() {
        this._isActive = false
    }
}