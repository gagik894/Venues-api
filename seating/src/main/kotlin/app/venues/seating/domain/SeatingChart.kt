package app.venues.seating.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener
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
@EntityListeners(AuditingEntityListener::class)
data class SeatingChart(
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

    ) : AbstractUuidEntity() {
    /**
     * Get all levels for this seating chart.
     * Levels are managed via repository queries, not bidirectional relationships.
     * This maintains proper module boundaries.
     */
    fun getLevels(levelRepository: app.venues.seating.repository.LevelRepository): List<Level> {
        return id.let { levelRepository.findBySeatingChartId(it) }
    }

    /**
     * Get all seats for this seating chart.
     * Seats are managed via repository queries, not bidirectional relationships.
     * This maintains proper module boundaries.
     */
    fun getSeats(seatRepository: app.venues.seating.repository.SeatRepository): List<Seat> {
        return id.let { seatRepository.findBySeatingChartId(it) }
    }

    /**
     * Get total capacity including GA areas and individual seats.
     * Requires repositories to be passed in - follows clean architecture.
     */
    fun getTotalCapacity(
        levelRepository: app.venues.seating.repository.LevelRepository,
        seatRepository: app.venues.seating.repository.SeatRepository
    ): Int {
        val levels = getLevels(levelRepository)
        val seats = getSeats(seatRepository)

        val gaCapacity = levels.filter { it.isGeneralAdmission() }.sumOf { it.capacity ?: 0 }
        val seatedCapacity = seats.size
        return gaCapacity + seatedCapacity
    }
}

