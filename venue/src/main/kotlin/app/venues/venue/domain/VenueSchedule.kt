package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Entity representing a venue schedule for a specific day of the week.
 *
 * Each venue can have different operating hours for each day of the week.
 * This allows flexible scheduling including closed days, different weekend hours, etc.
 */
@Entity
@Table(
    name = "venue_schedules",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_schedule_venue_day",
            columnNames = ["venue_id", "day_of_week"]
        )
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenueSchedule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The venue this schedule belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * Day of the week (MONDAY, TUESDAY, etc.)
     */
    @Column(name = "day_of_week", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    var dayOfWeek: DayOfWeek,

    /**
     * Opening time (null if closed)
     */
    @Column(name = "open_time")
    var openTime: java.time.LocalTime? = null,

    /**
     * Closing time (null if closed)
     */
    @Column(name = "close_time")
    var closeTime: java.time.LocalTime? = null,

    /**
     * Whether the venue is closed on this day
     */
    @Column(name = "is_closed", nullable = false)
    var isClosed: Boolean = false,

    // ===========================================
    // Audit Fields
    // ===========================================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
)

/**
 * Days of the week enum for venue schedules
 */
enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
