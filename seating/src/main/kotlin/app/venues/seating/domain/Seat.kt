package app.venues.seating.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener

/**
 * Seat entity representing an individual bookable seat.
 *
 * Seats belong to a Level (section) and can appear in multiple SeatingCharts.
 * Each seat has positioning data for visual rendering and identification data
 * for booking and API communication.
 */
@Entity
@Table(
    name = "seats",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_seat_level_identifier",
            columnNames = ["level_id", "seat_identifier"]
        )
    ],
    indexes = [
        Index(name = "idx_seat_level_id", columnList = "level_id"),
        Index(name = "idx_seat_identifier", columnList = "seat_identifier"),
    ]
)
@EntityListeners(AuditingEntityListener::class)
class Seat(
    /**
     * The level (section) this seat belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    var level: Level,

    /**
     * Unique identifier within the level (e.g., "A1", "B12")
     * Used for API communication and booking references
     * MUST be indexed for performance
     */
    @Column(name = "seat_identifier", nullable = false, length = 50)
    var seatIdentifier: String,

    /**
     * Display seat number (e.g., "1", "12", "A")
     */
    @Column(name = "seat_number", length = 50)
    var seatNumber: String? = null,

    /**
     * Row label (e.g., "A", "B", "1", "2")
     * Used with "Row" prefix for display (e.g., "Row A")
     * Optional - for display purposes
     */
    @Column(name = "row_label", length = 50)
    var rowLabel: String? = null,

    /**
     * X coordinate for rendering the seat on the chart
     */
    @Column(name = "position_x")
    var positionX: Double? = null,

    /**
     * Y coordinate for rendering the seat on the chart
     */
    @Column(name = "position_y")
    var positionY: Double? = null,

    ) : AbstractLongEntity() {
    /**
     * Get full seat display identifier (e.g., "Row A - Seat 12")
     */
    fun getFullDisplayName(): String {
        return buildString {
            if (rowLabel != null) {
                append("Row ")
                append(rowLabel)
                append(" - ")
            }
            if (seatNumber != null) {
                append("Seat ")
                append(seatNumber)
            } else {
                append(seatIdentifier)
            }
        }
    }
}

