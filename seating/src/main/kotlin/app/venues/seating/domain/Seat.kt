package app.venues.seating.domain

import app.venues.common.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * A single, individual seat within a Level.
 * This is a high-volume child entity (uses AbstractLongEntity).
 *
 * @param level The parent Level this seat belongs to.
 * @param seatIdentifier The unique ID within the level (e.g., "A1").
 * @param seatNumber The display seat number (e.g., "1").
 * @param rowLabel The display row label (e.g., "A").
 * @param positionX The X coordinate for seat placement on the chart.
 * @param positionY The Y coordinate for seat placement on the chart.
 */
@Entity
@Table(
    name = "seats",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_seat_level_identifier",
            columnNames = ["level_id", "seat_identifier"]
        )
    ]
)
class Seat(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    var level: Level,

    @Column(name = "seat_identifier", nullable = false, length = 50)
    var seatIdentifier: String,

    @Column(name = "seat_number", length = 50)
    var seatNumber: String? = null,

    @Column(name = "row_label", length = 50)
    var rowLabel: String? = null,

    @Column(name = "position_x")
    var positionX: Double? = null,

    @Column(name = "position_y")
    var positionY: Double? = null,

    ) : AbstractLongEntity() {

    fun getFullDisplayName(): String {
        return buildString {
            if (rowLabel != null) {
                append("Row ")
                append(rowLabel)
            }
            if (seatNumber != null) {
                if (rowLabel != null) append(" - ")
                append("Seat ")
                append(seatNumber)
            }
            if (rowLabel == null && seatNumber == null) {
                append(seatIdentifier)
            }
        }
    }
}