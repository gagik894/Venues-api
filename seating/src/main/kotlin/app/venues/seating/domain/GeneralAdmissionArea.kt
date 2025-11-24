package app.venues.seating.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Represents a defined area without assigned seating (General Admission).
 * Examples: "Standing Pit", "Lawn", "Open Floor".
 *
 * **Inventory Logic:**
 * Unlike [ChartSeat] (which is 1 entity = 1 ticket), a GA Area allows multiple bookings
 * against a single entity ID, up to the defined [capacity].
 *
 * @property zone The [ChartZone] containing this area.
 * @property name The display name (e.g., "Front Stage Pit").
 * @property code The Business Key (e.g., "ORCH_GA-PIT"). Used by APIs to book access.
 * @property capacity The absolute maximum number of people allowed in this area.
 * @property boundaryPath Polygon/SVG data defining the clickable region on the map.
 */
@Entity
@Table(
    name = "chart_ga_areas",
    indexes = [
        Index(name = "idx_ga_code", columnList = "code")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_ga_zone_code", columnNames = ["zone_id", "code"])
    ]
)
class GeneralAdmissionArea(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    var zone: ChartZone,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "code", nullable = false, length = 150, updatable = false)
    val code: String,

    @Column(name = "capacity", nullable = false)
    var capacity: Int,

    @Column(name = "category_key", nullable = false, length = 100)
    var categoryKey: String = "STANDARD",

    @Column(name = "boundary_path", columnDefinition = "TEXT")
    var boundaryPath: String? = null,

    @Column(name = "display_color", length = 7)
    var displayColor: String? = null

) : AbstractLongEntity() {

    // =================================================================================
    // Invariants
    // =================================================================================

    init {
        require(capacity > 0) { "General Admission capacity must be positive" }
        require(code.isNotBlank()) { "Business Key (fullCode) cannot be empty" }
    }

    // =================================================================================
    // Domain Behaviors
    // =================================================================================

    /**
     * Updates the maximum capacity for this area.
     *
     * @param newCapacity The new limit.
     * @throws IllegalArgumentException if capacity is not positive.
     */
    fun updateCapacity(newCapacity: Int) {
        require(newCapacity > 0) { "Capacity must be positive" }
        this.capacity = newCapacity
    }
}
