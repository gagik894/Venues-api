package app.venues.seating.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.*

/**
 * Seating Chart entity representing a venue's seating layout template.
 *
 * A seating chart is a reusable template that defines the structure of seats and sections
 * in a venue. Multiple events can use the same seating chart.
 *
 * Features:
 * - Visual rendering configuration (indicator sizes, background)
 * - Hierarchical sections (levels)
 * - Individual seats or GA (General Admission) areas
 * - Multi-language support via translations
 *
 * Cross-module relationships:
 * - venueId references venue module
 */
@Entity
@Table(
    name = "seating_charts",
    indexes = [
        Index(name = "idx_seating_chart_venue_id", columnList = "venue_id"),
        Index(name = "idx_seating_chart_name", columnList = "name")
    ]
)
class SeatingChart(
    /**
     * Venue ID - references venue module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "venue_id", nullable = false)
    var venueId: UUID,

    /**
     * Name of the seating chart (e.g., "Main Hall", "Balcony Layout")
     */
    @Column(nullable = false, length = 255)
    var name: String,

    /**
     * Size multiplier for seat indicators in the visual renderer
     */
    @Column(name = "seat_indicator_size", nullable = false)
    var seatIndicatorSize: Int = 1,

    /**
     * Size multiplier for level/section indicators in the visual renderer
     */
    @Column(name = "level_indicator_size", nullable = false)
    var levelIndicatorSize: Int = 1,

    /**
     * Background image URL for the seating chart visualization
     */
    @Column(name = "background_url", length = 500)
    var backgroundUrl: String? = null,
) : AbstractUuidEntity()

