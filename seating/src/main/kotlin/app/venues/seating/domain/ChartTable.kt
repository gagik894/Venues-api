package app.venues.seating.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Represents a physical table object placed on the chart.
 *
 * **Architectural Note:**
 * This entity defines only the *physical* characteristics (shape, capacity, location).
 * It does **not** dictate whether the table is sold as a unit or as individual seats.
 * That commercial logic is defined in the `EventSession` configuration.
 *
 * @property zone The [ChartZone] where this table is located.
 * @property tableNumber The display number/name (e.g., "T-12").
 * @property code The Business Key (e.g., "VIP_TABLE-12"). Used if the table is sold as a unit.
 * @property seatCapacity The number of chairs physically at this table.
 * @property shape The geometric shape for rendering.
 * @property width The physical width (or diameter) of the table in coordinate units.
 * @property height The physical height of the table in coordinate units.
 */
@Entity
@Table(
    name = "chart_tables",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_table_zone_code", columnNames = ["zone_id", "code"])
    ]
)
class ChartTable(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    var zone: ChartZone,

    @Column(name = "table_number", nullable = false, length = 20)
    var tableNumber: String,

    @Column(name = "code", nullable = false, length = 150)
    var code: String,

    @Column(name = "seat_capacity", nullable = false)
    var seatCapacity: Int = 4,

    @Column(name = "shape", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var shape: TableShape = TableShape.ROUND,

    @Column(name = "category_key", nullable = false, length = 100)
    var categoryKey: String = "STANDARD",

    // --- Rendering Attributes ---

    @Column(name = "x_position", nullable = false)
    var x: Double,

    @Column(name = "y_position", nullable = false)
    var y: Double,

    @Column(name = "width", nullable = false)
    var width: Double,

    @Column(name = "height", nullable = false)
    var height: Double,

    @Column(name = "rotation")
    var rotation: Double = 0.0

) : AbstractLongEntity() {

    @OneToMany(mappedBy = "table", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _seats: MutableList<ChartSeat> = mutableListOf()

    /**
     * Public read-only list of seats physically attached to this table.
     */
    val seats: List<ChartSeat> get() = _seats.toList()

    /**
     * Adds a seat to this table and ensures it is also added to the parent zone.
     */
    fun attachSeat(seat: ChartSeat) {
        if (seat.zone != this.zone) {
            throw IllegalArgumentException("Seat must belong to the same Zone as the Table")
        }
        _seats.add(seat)
        seat.table = this
    }
}

enum class TableShape { ROUND, SQUARE, RECTANGLE, OVAL }
