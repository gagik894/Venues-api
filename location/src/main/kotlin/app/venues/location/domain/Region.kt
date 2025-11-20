package app.venues.location.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Administrative Region reference data entity.
 *
 * This entity stores government-recognized administrative regions (provinces, states, etc.)
 * with multilingual support for interoperability with other government systems.
 *
 * Design Principles:
 * - Reference Data: Uses 'ref_' table prefix to indicate static/semi-static data
 * - ISO Compliance: Code field follows ISO 3166-2 or government-standard codes
 * - Multilingual: Names stored as JSONB for flexible language support
 * - Immutable: Regions rarely change; updates require database migration
 *
 * Data Format:
 * - code: ISO/Government code (e.g., "AM-ER" for Yerevan, "AM-SH" for Shirak)
 * - names: {"hy": "Երևան", "en": "Yerevan", "ru": "Ереван"}
 *
 * Interoperability:
 * The code field enables integration with other government systems (tax, cadastre, etc.)
 * that use standardized region identifiers.
 *
 * @property code Official ISO or government-assigned region code (unique identifier)
 * @property names Multilingual region names stored as JSONB map
 */
@Entity
@Table(
    name = "ref_regions",
    indexes = [
        Index(name = "idx_region_code", columnList = "code")
    ]
)
class Region(
    /**
     * Official ISO or Government Code.
     *
     * Examples:
     * - "AM-ER" (Yerevan - capital city region)
     * - "AM-SH" (Shirak Province)
     * - "AM-LO" (Lori Province)
     *
     * Critical for interoperability with other government systems
     * (National Statistics Service, Cadastre, Tax authorities, etc.).
     */
    @Column(name = "code", nullable = false, unique = true, length = 10)
    var code: String,

    /**
     * Multilingual region names.
     *
     * Stored as JSONB for efficient querying and indexing.
     * Format: {"hy": "Շիրակ", "en": "Shirak", "ru": "Ширак"}
     *
     * Best Practice: Always include at least "hy" (Armenian) and "en" (English).
     */
    @Column(name = "names", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var names: Map<String, String> = mutableMapOf(),

    /**
     * Optional display order for UI sorting.
     * Allows presenting regions in a specific order (e.g., capital first).
     */
    @Column(name = "display_order")
    var displayOrder: Int? = null,

    /**
     * Active status flag.
     * Allows soft-disabling regions without deletion (for historical data integrity).
     */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

) : AbstractLongEntity() {

    /**
     * Get region name in specified language with fallback.
     *
     * Fallback order: requested language → English → first available → code
     *
     * @param lang Language code (e.g., "hy", "en", "ru")
     * @return Localized region name or code if no translation exists
     */
    fun getName(lang: String): String {
        return names[lang]
            ?: names["en"]
            ?: names.values.firstOrNull()
            ?: code
    }

    /**
     * Get Armenian name (official language).
     */
    fun getArmenianName(): String = getName("hy")

    /**
     * Get English name (international language).
     */
    fun getEnglishName(): String = getName("en")

    override fun toString(): String {
        return "Region(id=$id, code='$code', names=$names)"
    }
}

