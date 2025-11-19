package app.venues.venue.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "ref_venue_categories")
class VenueCategory(

    /**
     * The System Key (Immutable).
     * e.g. "OPERA", "THEATRE", "MUSEUM"
     */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    var code: String,

    /**
     * Multilingual Names stored as JSON.
     * Data: {"en": "Opera House", "hy": "Օպերային Թատրոն"}
     */
    @Column(name = "names", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var names: Map<String, String> = mutableMapOf(),

    /**
     * UI Color (Hex). e.g. "#E91E63"
     */
    @Column(name = "color", length = 7)
    var color: String? = null,

    /**
     * Material Design / FontAwesome icon name.
     * e.g. "theater-masks"
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