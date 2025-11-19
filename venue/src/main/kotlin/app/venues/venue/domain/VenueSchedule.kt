package app.venues.venue.domain

import app.venues.shared.persistence.domain.AbstractLongEntity
import jakarta.persistence.*

/**
 * An operating schedule entry for a Venue (e.g., "Monday: 9am-5pm").
 * This is a child entity of Venue.
 *
 * @param venue The venue this schedule belongs to.
 * @param dayOfWeek The day of the week.
 * @param openTime The opening time (nullable if closed).
 * @param closeTime The closing time (nullable if closed).
 * @param isClosed Whether the venue is closed on this day.
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
class VenueSchedule(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    @Column(name = "day_of_week", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    var dayOfWeek: DayOfWeek,

    @Column(name = "open_time")
    var openTime: java.time.LocalTime? = null,

    @Column(name = "close_time")
    var closeTime: java.time.LocalTime? = null,

    @Column(name = "is_closed", nullable = false)
    var isClosed: Boolean = false,

    ) : AbstractLongEntity()

/**
 * Days of the week enum for venue schedules
 */
enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
