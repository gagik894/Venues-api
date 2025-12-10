package app.venues.seating.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Represents a logical container or grouping within a [SeatingChart].
 *
 * Zones act as the primary organizational unit (e.g., "Orchestra", "Balcony", "VIP Box").
 * They can be hierarchical (Zones containing Zones).
 *
 * Crucially, the Zone defines the **Prefix** (`code`) for the Business Keys of all
 * inventory items (seats, tables) contained within it.
 *
 * @property chart The parent [SeatingChart].
 * @property parentZone The parent [ChartZone] if this is a sub-section (e.g., "Row A" inside "Orchestra"). Nullable.
 * @property name The display name (e.g., "Orchestra Center").
 * @property code The immutable, short alphanumeric identifier (e.g., "ORCH_C"). Used as an API Key prefix.
 * @property x The X coordinate of the zone container's top-left corner.
 * @property y The Y coordinate of the zone container's top-left corner.
 * @property rotation The rotation angle in degrees.
 * @property boundaryPath SVG Path data defining the clickable shape of the zone container.
 * @property displayColor Hex color code for rendering the zone grouping.
 */
@Entity
@Table(
    name = "chart_zones",
    uniqueConstraints = [
        // Ensures Zone Codes are unique within a single Chart to guarantee API Key uniqueness.
        UniqueConstraint(name = "uk_zone_chart_code", columnNames = ["chart_id", "code"])
    ]
)
class ChartZone(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chart_id", nullable = false)
    var chart: SeatingChart,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_zone_id")
    var parentZone: ChartZone? = null,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "code", nullable = false, length = 50, updatable = false)
    val code: String,

    // --- Rendering Attributes ---

    @Column(name = "x_position", nullable = false)
    var x: Double = 0.0,

    @Column(name = "y_position", nullable = false)
    var y: Double = 0.0,

    @Column(name = "rotation")
    var rotation: Double = 0.0,

    @Column(name = "boundary_path", columnDefinition = "TEXT")
    var boundaryPath: String? = null,

    @Column(name = "display_color", length = 7)
    var displayColor: String? = null

) : AbstractLongEntity() {

    // =================================================================================
    // Internal State & Encapsulation
    // =================================================================================

    @OneToMany(mappedBy = "parentZone", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _childZones: MutableList<ChartZone> = mutableListOf()

    @OneToMany(mappedBy = "zone", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _seats: MutableList<ChartSeat> = mutableListOf()

    @OneToMany(mappedBy = "zone", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _tables: MutableList<ChartTable> = mutableListOf()

    @OneToMany(mappedBy = "zone", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _gaAreas: MutableList<GeneralAdmissionArea> = mutableListOf()

    // --- Public Views ---

    val childZones: List<ChartZone> get() = _childZones.toList()
    val seats: List<ChartSeat> get() = _seats.toList()
    val tables: List<ChartTable> get() = _tables.toList()
    val gaAreas: List<GeneralAdmissionArea> get() = _gaAreas.toList()

    // =================================================================================
    // Domain Behaviors
    // =================================================================================

    /**
     * Adds a child sub-zone.
     */
    fun addChildZone(zone: ChartZone) {
        if (zone.chart != this.chart) {
            throw IllegalArgumentException("Child zone must belong to the same chart")
        }
        _childZones.add(zone)
        zone.parentZone = this
    }

    /**
     * Adds a seat to this zone.
     *
     * Validates that the seat's business key (`fullCode`) is properly prefixed with
     * this zone's [code] to ensure global API consistency.
     *
     * @throws IllegalArgumentException if the seat code format is invalid.
     */
    fun addSeat(seat: ChartSeat) {
        val expectedPrefix = SeatCodeFormatter.buildZonePrefix(this)
        if (!seat.code.startsWith(expectedPrefix)) {
            throw IllegalArgumentException(
                "Seat code '${seat.code}' invalid. Must start with zone hierarchy prefix '${expectedPrefix}'."
            )
        }
        _seats.add(seat)
        seat.zone = this
    }

    /**
     * Adds a physical table to this zone.
     */
    fun addTable(table: ChartTable) {
        _tables.add(table)
        table.zone = this
    }

    /**
     * Adds a general admission area to this zone.
     */
    fun addGaArea(gaArea: GeneralAdmissionArea) {
        _gaAreas.add(gaArea)
        gaArea.zone = this
    }

    /**
     * Helper to generate a standardized API Key for a seat in this zone.
     * Format: `{ZONE_CODE}_ROW-{ROW}_SEAT-{NUMBER}`
     */
    fun generateSeatKey(row: String, number: String): String {
        return SeatCodeFormatter.buildSeatCode(this, row, number)
    }
}
