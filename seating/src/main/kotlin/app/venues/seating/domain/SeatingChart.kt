package app.venues.seating.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.util.*

/**
 * Represents the canvas and coordinate system for a specific venue layout.
 *
 * This is the aggregate root for the seating domain. It defines the physical boundaries
 * and coordinate space (width/height) used by client applications to render the map.
 *
 * @property venueId The [UUID] of the venue this chart belongs to. Immutable.
 * @property name The internal display name (e.g., "Concert Mode - Full Capacity").
 * @property width The width of the coordinate system canvas (e.g., 2000). Must be positive.
 * @property height The height of the coordinate system canvas (e.g., 2000). Must be positive.
 * @property isActive Indicates if this chart is selectable for new events.
 * @property backgroundUrl Optional URL to a static background image (e.g., floor plan blueprint).
 * @property styleConfigJson JSON blob containing global rendering styles (fonts, default colors) to allow frontend flexibility.
 */
@Entity
@Table(
    name = "seating_charts",
    indexes = [
        Index(name = "idx_chart_venue", columnList = "venue_id"),
        Index(name = "idx_chart_active", columnList = "is_active")
    ]
)
class SeatingChart(
    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "width", nullable = false)
    var width: Int = 2000,

    @Column(name = "height", nullable = false)
    var height: Int = 2000,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "background_url", length = 500)
    var backgroundUrl: String? = null,

    @Column(name = "style_config_json", columnDefinition = "TEXT")
    var styleConfigJson: String? = null

) : AbstractUuidEntity() {

    // =================================================================================
    // Internal State & Encapsulation
    // =================================================================================

    /**
     * The list of top-level zones (or all zones, depending on query depth) associated with this chart.
     * Access is encapsulated to ensure referential integrity when adding/removing zones.
     */
    @OneToMany(mappedBy = "chart", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _zones: MutableList<ChartZone> = mutableListOf()

    /**
     * Public read-only view of the zones.
     */
    val zones: List<ChartZone>
        get() = _zones.toList()

    // =================================================================================
    // Invariants & Validation
    // =================================================================================

    init {
        require(width > 0) { "Chart width must be greater than 0" }
        require(height > 0) { "Chart height must be greater than 0" }
    }

    // =================================================================================
    // Domain Behaviors
    // =================================================================================

    /**
     * Adds a structural zone to the chart.
     * Automatically sets the back-reference from the zone to this chart.
     *
     * @param zone The [ChartZone] to add.
     * @throws IllegalArgumentException if the zone is already attached to another chart.
     */
    fun addZone(zone: ChartZone) {
        if (zone.chart != null && zone.chart != this) {
            throw IllegalArgumentException("Zone belongs to a different chart")
        }
        if (!_zones.contains(zone)) {
            zone.chart = this
            _zones.add(zone)
        }
    }

    /**
     * Resizes the coordinate canvas.
     *
     * @param newWidth The new width (must be > 0).
     * @param newHeight The new height (must be > 0).
     */
    fun resizeCanvas(newWidth: Int, newHeight: Int) {
        require(newWidth > 0 && newHeight > 0) { "Dimensions must be positive" }
        this.width = newWidth
        this.height = newHeight
    }

    /**
     * Deactivates the chart, preventing it from being selected for future events.
     * Existing events using this chart remain unaffected.
     */
    fun archive() {
        this.isActive = false
    }
}
