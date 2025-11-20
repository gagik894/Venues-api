package app.venues.seating.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Represents a single, addressable seat within a [ChartZone].
 *
 * This entity contains the specific coordinates for drawing the seat and the
 * deterministic Business Key (`fullCode`) used by external APIs to book it.
 *
 * @property zone The parent [ChartZone].
 * @property table The [ChartTable] this seat is physically attached to (optional).
 * @property rowLabel The display label for the row (e.g., "A", "AA").
 * @property seatNumber The display number for the seat (e.g., "1", "101").
 * @property code The Fully Qualified Business Key (e.g., "ORCH_ROW-A_SEAT-1"). Must be unique within the Zone.
 * @property categoryKey A string reference to a pricing category (e.g. "VIP", "STANDARD").
 * @property isAccessible Whether this seat is wheelchair accessible (ADA compliance).
 * @property isObstructedView Whether the view from this seat is partially blocked.
 * @property x The absolute X coordinate relative to the Zone's origin.
 * @property y The absolute Y coordinate relative to the Zone's origin.
 * @property rotation The rotation of the seat in degrees.
 */
@Entity
@Table(
    name = "chart_seats",
    indexes = [
        Index(name = "idx_seat_code", columnList = "code"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_seat_zone_code", columnNames = ["zone_id", "code"])
    ]
)
class ChartSeat(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    var zone: ChartZone,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    var table: ChartTable? = null,

    @Column(name = "row_label", nullable = false, length = 20)
    var rowLabel: String,

    @Column(name = "seat_number", nullable = false, length = 20)
    var seatNumber: String,

    @Column(name = "code", nullable = false, length = 150)
    var code: String,

    @Column(name = "category_key", nullable = false, length = 50)
    var categoryKey: String = "STANDARD",

    @Column(name = "is_accessible", nullable = false)
    var isAccessible: Boolean = false,

    @Column(name = "is_obstructed_view", nullable = false)
    var isObstructedView: Boolean = false,

    // --- Rendering Attributes ---

    @Column(name = "x_position", nullable = false)
    var x: Double,

    @Column(name = "y_position", nullable = false)
    var y: Double,

    @Column(name = "rotation")
    var rotation: Double = 0.0

) : AbstractLongEntity() {

    /**
     * Convenience constructor that automatically generates the [code].
     */
    constructor(
        zone: ChartZone,
        row: String,
        number: String,
        x: Double,
        y: Double,
        category: String = "STANDARD"
    ) : this(
        zone = zone,
        rowLabel = row,
        seatNumber = number,
        code = zone.generateSeatKey(row, number),
        categoryKey = category,
        x = x,
        y = y
    )

    // =================================================================================
    // Invariants
    // =================================================================================

    init {
        // Integrity Check: Ensure the code stored actually matches the seat data.
        // This prevents developer error where row="A" but code="ROW-B".
        val expectedKeyPart =
            "ROW-${rowLabel.replace(" ", "").uppercase()}_SEAT-${seatNumber.replace(" ", "").uppercase()}"
        require(code.contains(expectedKeyPart)) {
            "Data Integrity Error: Seat code '$code' does not match Row '$rowLabel' and Number '$seatNumber'"
        }
    }

    // =================================================================================
    // Domain Behaviors
    // =================================================================================

    /**
     * Relocates the seat on the canvas.
     */
    fun move(newX: Double, newY: Double, newRotation: Double = this.rotation) {
        this.x = newX
        this.y = newY
        this.rotation = newRotation
    }

    /**
     * Renames the seat (e.g., fixing a typo in row label).
     *
     * **CRITICAL SIDE EFFECT**: This modifies the [code].
     * If this seat has already been sold in a future event using the old code,
     * this operation might break external API references unless handled by a higher-level migration service.
     */
    fun rename(newRow: String, newNumber: String) {
        this.rowLabel = newRow
        this.seatNumber = newNumber
        // Update the deterministic key to match the new reality
        this.code = zone.generateSeatKey(newRow, newNumber)
    }
}
