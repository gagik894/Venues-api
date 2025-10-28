package app.venues.seating.domain

import app.venues.venue.domain.Venue
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The venue this seating chart belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

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

    /**
     * Levels (sections/areas) in this seating chart
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "level_seating_charts",
        joinColumns = [JoinColumn(name = "seating_chart_id")],
        inverseJoinColumns = [JoinColumn(name = "level_id")]
    )
    var levels: MutableSet<Level> = mutableSetOf(),

    /**
     * Seats included in this seating chart
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "seat_seating_charts",
        joinColumns = [JoinColumn(name = "seating_chart_id")],
        inverseJoinColumns = [JoinColumn(name = "seat_id")]
    )
    var seats: MutableSet<Seat> = mutableSetOf(),

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    /**
     * Add a level to this seating chart
     */
    fun addLevel(level: Level) {
        levels.add(level)
        level.seatingCharts.add(this)
    }

    /**
     * Remove a level from this seating chart
     */
    fun removeLevel(level: Level) {
        levels.remove(level)
        level.seatingCharts.remove(this)
    }

    /**
     * Add a seat to this seating chart
     */
    fun addSeat(seat: Seat) {
        seats.add(seat)
        seat.seatingCharts.add(this)
    }

    /**
     * Remove a seat from this seating chart
     */
    fun removeSeat(seat: Seat) {
        seats.remove(seat)
        seat.seatingCharts.remove(this)
    }

    /**
     * Get total capacity including GA areas and individual seats
     */
    fun getTotalCapacity(): Int {
        val gaCapacity = levels.filter { it.isGeneralAdmission() }.sumOf { it.capacity ?: 0 }
        val seatedCapacity = seats.size
        return gaCapacity + seatedCapacity
    }
}

