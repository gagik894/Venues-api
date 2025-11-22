package app.venues.event.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * An internal definition for an event category.
 * This is a "child" or "internal" entity (uses AbstractLongEntity).
 * Its public-facing ID is the `categoryKey`.
 *
 * @param code The System Key (Immutable). Renamed from 'categoryKey' to 'code' to match VenueCategory.
 * @param names Multilingual Names stored as JSON.
 * @param color An optional color code (e.g., "#FF5733") for UI representation.
 * @param icon An optional icon name or URL for UI representation.
 * @param displayOrder The order in which this category should be displayed.
 */
@Entity
@Table(name = "ref_event_categories")
class EventCategory(

    /**
     * The System Key.
     * Renamed from 'categoryKey' to 'code' to match VenueCategory.
     */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    var code: String,

    /**
     * Multilingual Names stored as JSON.
     * Replaces the @OneToMany translation relationship.
     */
    @Column(name = "names", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var names: Map<String, String> = mutableMapOf(),

    @Column(name = "color", length = 7)
    var color: String? = null,

    /**
     * Icon length normalized to 50 to match VenueCategory.
     */
    @Column(name = "icon", length = 50)
    var icon: String? = null,

    @Column(name = "display_order")
    var displayOrder: Int = 0,

    @Column(name = "is_active")
    var isActive: Boolean = true

) : AbstractLongEntity() {

    /**
     * Helper to get the name in the requested language.
     * Falls back to English, then to the Code.
     */
    fun getName(language: String): String {
        return names[language] ?: names["en"] ?: code
    }
}
