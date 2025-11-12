package app.venues.seating.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Level entity representing a section or area in a seating chart.
 *
 * Levels can be:
 * 1. **General Admission (GA)**: Has capacity and position, no individual seats
 * 2. **Seated Section**: Contains individual Seat entities
 * 3. **Parent Section**: Can contain child levels (nested hierarchy)
 *
 * Examples:
 * - Orchestra (seated section with seats A1, A2, etc.)
 * - Standing Area (GA with capacity = 100)
 * - Balcony (parent) → Balcony Left (child), Balcony Right (child)
 */
@Entity
@Table(
    name = "levels",
    indexes = [
        Index(name = "idx_level_seating_chart_id", columnList = "seating_chart_id"),
        Index(name = "idx_level_parent_id", columnList = "parent_level_id"),
        Index(name = "idx_level_identifier", columnList = "level_identifier")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Level(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * Parent level for hierarchical nesting (null for top-level)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_level_id")
    var parentLevel: Level? = null,

    /**
     * Seating chart ID this level belongs to.
     * Stored as ID to maintain proper module boundaries.
     */
    @Column(name = "seating_chart_id", nullable = false)
    var seatingChartId: Long,

    /**
     * Name of the level (default language)
     */
    @Column(name = "level_name", nullable = false, length = 255)
    var levelName: String,

    /**
     * Unique identifier for this level (e.g., "ORCH", "BALC", "VIP")
     * Used for API communication and references
     */
    @Column(name = "level_identifier", length = 50)
    var levelIdentifier: String? = null,


    /**
     * X coordinate for rendering (for GA sections and visual positioning)
     */
    @Column(name = "position_x")
    var positionX: Double? = null,

    /**
     * Y coordinate for rendering (for GA sections and visual positioning)
     */
    @Column(name = "position_y")
    var positionY: Double? = null,

    /**
     * Capacity for General Admission areas (null for seated sections)
     */
    @Column(name = "capacity")
    var capacity: Int? = null,

    /**
     * Indicates if this level represents a table (group of seats sold as a unit)
     */
    @Column(name = "is_table", nullable = false)
    var isTable: Boolean = false,

    /**
     * Table booking mode (how the table can be booked)
     * Only applicable if isTable = true
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "table_booking_mode", length = 20)
    var tableBookingMode: TableBookingMode? = null,

    /**
     * Number of seats in the table (for validation and display)
     * Only applicable if isTable = true
     */
    @Column(name = "table_capacity")
    var tableCapacity: Int? = null,


    /**
     * Child levels (for nested hierarchy)
     */
    @OneToMany(mappedBy = "parentLevel", cascade = [CascadeType.ALL], orphanRemoval = true)
    var childLevels: MutableList<Level> = mutableListOf(),

    /**
     * Seats in this level (for seated sections only)
     */
    @OneToMany(mappedBy = "level", cascade = [CascadeType.ALL], orphanRemoval = true)
    var seats: MutableList<Seat> = mutableListOf(),

    /**
     * Translations for level label
     */
    @OneToMany(mappedBy = "level", cascade = [CascadeType.ALL], orphanRemoval = true)
    var translations: MutableList<LevelTranslation> = mutableListOf(),

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
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
        return isTable && tableBookingMode != null
    }

    /**
     * Check if table allows individual seat booking
     */
    fun allowsSeatBooking(): Boolean {
        return isTable && (tableBookingMode == TableBookingMode.SEATS_ONLY ||
                tableBookingMode == TableBookingMode.FLEXIBLE)
    }

    /**
     * Check if table allows whole table booking
     */
    fun allowsTableBooking(): Boolean {
        return isTable && (tableBookingMode == TableBookingMode.TABLE_ONLY ||
                tableBookingMode == TableBookingMode.FLEXIBLE)
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

