package app.venues.seating.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.*

/**
 * A "root" definition for a reusable seating chart template.
 *
 * @param venueId The `Venue.id` (a UUID) this chart belongs to.
 * @param name The name of the chart (e.g., "Main Hall").
 * @param seatIndicatorSize The size of seat indicators on the chart.
 * @param levelIndicatorSize The size of level indicators on the chart.
 * @param backgroundUrl An optional background image URL for the chart.
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
    @Column(name = "venue_id", nullable = false)
    var venueId: UUID,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "seat_indicator_size", nullable = false)
    var seatIndicatorSize: Int = 1,

    @Column(name = "level_indicator_size", nullable = false)
    var levelIndicatorSize: Int = 1,

    @Column(name = "background_url", length = 500)
    var backgroundUrl: String? = null,

    ) : AbstractUuidEntity()