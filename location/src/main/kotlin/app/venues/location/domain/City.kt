package app.venues.location.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * City/Community reference data entity.
 *
 * This entity stores government-recognized cities and communities with strict
 * linkage to their parent administrative regions. Supports multilingual names
 * and optional integration with official cadastre systems.
 *
 * Design Principles:
 * - Reference Data: Uses 'ref_' table prefix to indicate static/semi-static data
 * - Regional Hierarchy: Every city belongs to exactly one region (mandatory)
 * - URL-Friendly: Slug field enables clean API endpoints (e.g., /cities/gyumri)
 * - Multilingual: Names stored as JSONB for flexible language support
 * - Cadastre Integration: Optional official_id for linking with national cadastre
 *
 * Data Format:
 * - slug: URL-safe identifier (e.g., "gyumri", "yerevan", "vanadzor")
 * - names: {"hy": "Գյումրի", "en": "Gyumri", "ru": "Гюмри"}
 * - official_id: Cadastre/government ID (optional, for advanced integration)
 *
 * Business Rules:
 * - Slug must be unique across all cities
 * - Slug should be lowercase, URL-safe (no spaces, special chars)
 * - At least Armenian (hy) and English (en) names should be provided
 * - Region association is mandatory and immutable after creation
 *
 * @property region Parent administrative region (mandatory, lazy-loaded)
 * @property slug URL-friendly unique identifier for API endpoints
 * @property names Multilingual city names stored as JSONB map
 * @property officialId Optional cadastre/government identifier for interoperability
 * @property isActive Soft-delete flag for historical data integrity
 */
@Entity
@Table(
    name = "ref_cities",
    indexes = [
        Index(name = "idx_city_slug", columnList = "slug"),
        Index(name = "idx_city_region", columnList = "region_id"),
        Index(name = "idx_city_active", columnList = "is_active")
    ]
)
class City(
    /**
     * Parent administrative region.
     *
     * Mandatory relationship ensuring every city belongs to a region.
     * Lazy-loaded to avoid unnecessary joins when only city data is needed.
     *
     * Example relationships:
     * - Gyumri → Shirak Province
     * - Yerevan → Yerevan City Region
     * - Vanadzor → Lori Province
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    var region: Region,

    /**
     * URL-friendly slug for APIs.
     *
     * Requirements:
     * - Lowercase ASCII characters only
     * - No spaces (use hyphens instead)
     * - Unique across all cities
     * - Should not change after creation (used in URLs)
     *
     * Examples: "gyumri", "yerevan", "vanadzor", "dilijan"
     *
     * Used in API endpoints: GET /api/v1/cities/{slug}
     */
    @Column(name = "slug", nullable = false, unique = true, length = 100)
    var slug: String,

    /**
     * Multilingual city names.
     *
     * Stored as JSONB for efficient querying and indexing.
     * Format: {"hy": "Գյումրի", "en": "Gyumri", "ru": "Гюмри"}
     *
     * Best Practice:
     * - Always include "hy" (Armenian - official language)
     * - Always include "en" (English - international language)
     * - Optionally include "ru" (Russian - widely used)
     *
     * The multilingual approach supports:
     * - Tourist-friendly interfaces
     * - Government interoperability
     * - International event promotion
     */
    @Column(name = "names", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var names: Map<String, String> = mutableMapOf(),

    /**
     * Official Cadastre ID (if available/needed).
     *
     * Optional identifier linking to national cadastre or other
     * government registries (National Statistics Service, Tax authorities).
     *
     * Enables data synchronization with:
     * - Ministry of Territorial Administration
     * - State Cadastre Committee
     * - National Statistical Service
     *
     * Format varies by system (alphanumeric, max 50 characters).
     */
    @Column(name = "official_id", length = 50)
    var officialId: String? = null,

    /**
     * Optional display order for UI sorting.
     *
     * Allows custom ordering (e.g., population size, alphabetical, importance).
     * Lower numbers appear first. Null values sorted last.
     */
    @Column(name = "display_order")
    var displayOrder: Int? = null,

    /**
     * Active status flag.
     *
     * Soft-delete mechanism:
     * - true: City is active and visible in UI
     * - false: City is archived but preserved for historical bookings/events
     *
     * Never hard-delete cities to maintain referential integrity
     * with existing bookings, events, and venues.
     */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

) : AbstractLongEntity() {

    /**
     * Get city name in specified language with intelligent fallback.
     *
     * Fallback order: requested language → English → Armenian → first available → slug
     *
     * @param lang Language code (e.g., "hy", "en", "ru")
     * @return Localized city name or slug if no translation exists
     */
    fun getName(lang: String): String {
        return names[lang]
            ?: names["en"]
            ?: names["hy"]
            ?: names.values.firstOrNull()
            ?: slug
    }

    /**
     * Get Armenian name (official language).
     */
    fun getArmenianName(): String = names["hy"] ?: slug

    /**
     * Get English name (international language).
     */
    fun getEnglishName(): String = names["en"] ?: slug

    /**
     * Check if city name matches search term (case-insensitive, multi-language).
     *
     * @param searchTerm Search string
     * @return true if any name or slug contains the search term
     */
    fun matchesSearch(searchTerm: String): Boolean {
        val lower = searchTerm.lowercase()
        return slug.contains(lower) || names.values.any { it.lowercase().contains(lower) }
    }

    override fun toString(): String {
        return "City(id=$id, slug='$slug', names=$names, regionId=${region.id})"
    }
}

