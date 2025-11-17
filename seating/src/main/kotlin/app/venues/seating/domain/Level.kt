package app.venues.seating.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * A Level (section, area, or table) within a SeatingChart.
 * This is a high-volume child entity (uses AbstractLongEntity).
 *
 * @param seatingChart The parent SeatingChart.
 * @param levelName The display name of the level (e.g., "Orchestra").
 * @param parentLevel The parent Level if this is a sub-level.
 * @param levelIdentifier An optional identifier for the level (e.g., "A", "B").
 * @param positionX The X coordinate for level placement on the chart.
 * @param positionY The Y coordinate for level placement on the chart.
 * @param capacity The capacity for General Admission levels (null if seated).
 * @param isTable Whether this level uses table configuration.
 */
@Entity
@Table(
    name = "levels",
    indexes = [
        Index(name = "idx_level_seating_chart_id", columnList = "seating_chart_id"),
        Index(name = "idx_level_parent_id", columnList = "parent_level_id")
    ]
)
class Level(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seating_chart_id", nullable = false)
    var seatingChart: SeatingChart,

    @Column(name = "level_name", nullable = false, length = 255)
    var levelName: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_level_id")
    var parentLevel: Level? = null,

    @Column(name = "level_identifier", length = 50)
    var levelIdentifier: String? = null,

    @Column(name = "position_x")
    var positionX: Double? = null,

    @Column(name = "position_y")
    var positionY: Double? = null,

    @Column(name = "capacity")
    var capacity: Int? = null,

    @Column(name = "is_table", nullable = false)
    var isTable: Boolean = false,

    ) : AbstractLongEntity() {

    @OneToMany(mappedBy = "parentLevel", cascade = [CascadeType.ALL], orphanRemoval = true)
    val childLevels: MutableList<Level> = mutableListOf()

    @OneToMany(mappedBy = "level", cascade = [CascadeType.ALL], orphanRemoval = true)
    val seats: MutableList<Seat> = mutableListOf()

    @OneToMany(mappedBy = "level", cascade = [CascadeType.ALL], orphanRemoval = true)
    val translations: MutableList<LevelTranslation> = mutableListOf()

    // --- Public Behaviors ---

    /**
     * Check if this is a General Admission area
     */
    fun isGeneralAdmission(): Boolean {
        return capacity != null && capacity!! > 0
    }

    /**
     * Check if this is a seated section
     */
    fun isSeatedSection(): Boolean {
        return !isGeneralAdmission() && !isTable && seats.isNotEmpty()
    }

    /**
     * Check if this is a table level (has table configuration)
     */
    fun isTableLevel(): Boolean {
        return isTable
    }

    /**
     * Check if this is a parent section (has children)
     */
    fun isParentSection(): Boolean {
        return childLevels.isNotEmpty()
    }

    /**
     * Get translated label for a specific language
     */
    fun getTranslatedLabel(language: String): String {
        return translations.find { it.language == language }?.levelLabel ?: levelName
    }

    /**
     * Add a translation
     */
    fun addTranslation(translation: LevelTranslation) {
        translations.add(translation)
        translation.level = this
    }

    /**
     * Add a child level
     */
    fun addChildLevel(child: Level) {
        childLevels.add(child)
        child.parentLevel = this
    }
}
