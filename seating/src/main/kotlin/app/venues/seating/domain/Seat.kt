package app.venues.seating.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

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
        Index(name = "idx_seat_type", columnList = "seat_type")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Seat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

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
     * Row label (e.g., "Row A", "Row B")
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

    /**
     * Default seat type (e.g., "standard", "vip", "wheelchair")
     * Can be overridden per event
     */
    @Column(name = "seat_type", length = 50)
    var seatType: String? = null,


    /**
     * Translations for seat label
     */
    @OneToMany(mappedBy = "seat", cascade = [CascadeType.ALL], orphanRemoval = true)
    var translations: MutableList<SeatTranslation> = mutableListOf(),

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    /**
     * Get translated label for a specific language
     */
    fun getTranslatedLabel(language: String): String? {
        return translations.find { it.language == language }?.label
    }

    /**
     * Add a translation
     */
    fun addTranslation(translation: SeatTranslation) {
        translations.add(translation)
        translation.seat = this
    }

    /**
     * Get full seat display identifier (e.g., "Row A - Seat 12")
     */
    fun getFullDisplayName(): String {
        return buildString {
            if (rowLabel != null) {
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

