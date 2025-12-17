package app.venues.seating.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * Represents a visual, non-bookable element on the seating chart.
 * Used for user orientation (Stage, Doors) and structural context (Pillars, Walls).
 *
 * Unlike Zones or Seats, Landmarks have no inventory logic; they are purely visual.
 *
 * @property chart The parent [SeatingChart].
 * @property label Display text (e.g., "Main Stage", "North Exit").
 * @property type The semantic type of the landmark, used by the frontend for icon/styling selection.
 * @property shapeType The geometric shape (RECTANGLE, CIRCLE, POLYGON, PATH).
 * @property x The X position relative to the chart origin.
 * @property y The Y position relative to the chart origin.
 * @property width Physical width (for Rect/Oval).
 * @property height Physical height (for Rect/Oval).
 * @property rotation Rotation in degrees.
 * @property boundaryPath SVG path data for complex shapes.
 * @property iconKey Optional reference to a frontend icon library (e.g., "fa-martini-glass").
 */
@Entity
@Table(
    name = "chart_landmarks",
    indexes = [Index(name = "idx_landmark_chart", columnList = "chart_id")]
)
class ChartLandmark(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chart_id", nullable = false)
    var chart: SeatingChart,

    @Column(name = "label", nullable = false, length = 100)
    var label: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    var type: LandmarkType,

    @Enumerated(EnumType.STRING)
    @Column(name = "shape_type", nullable = false, length = 50)
    var shapeType: LandmarkShapeType = LandmarkShapeType.RECTANGLE,

    // --- Rendering Attributes ---

    @Column(name = "x_position", nullable = false)
    var x: Double,

    @Column(name = "y_position", nullable = false)
    var y: Double,

    @Column(name = "width")
    var width: Double? = null,

    @Column(name = "height")
    var height: Double? = null,

    @Column(name = "rotation")
    var rotation: Double = 0.0,

    @Column(name = "boundary_path", columnDefinition = "TEXT")
    var boundaryPath: String? = null,

    @Column(name = "icon_key", length = 100)
    var iconKey: String? = null

) : AbstractLongEntity()

enum class LandmarkType {
    STAGE,
    SCREEN,
    ENTRANCE,
    EXIT,
    BAR,
    RESTROOM,
    PILLAR,
    STAIRS,
    ELEVATOR,
    OTHER
}

enum class LandmarkShapeType {
    RECTANGLE,
    CIRCLE,
    ELLIPSE,
    POLYGON,
    PATH,
    ICON
}