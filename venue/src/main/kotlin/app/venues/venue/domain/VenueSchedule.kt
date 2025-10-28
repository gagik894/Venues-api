package app.venues.venue.domain

import jakarta.persistence.*
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Venue Schedule Entity
 *
 * Represents the operating hours for a specific day of the week.
 * Each venue can have multiple schedules (one per day).
 *
 * Examples:
 * - Monday: 09:00 - 18:00
 * - Tuesday: Closed
 * - Saturday: 10:00 - 22:00
 */
@Entity
@Table(
    name = "venue_schedules",
    indexes = [
        Index(name = "idx_venue_schedule_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_schedule_day", columnList = "day_of_week")
    ]
)
data class VenueSchedule(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * Day of the week
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    var dayOfWeek: DayOfWeek,

    /**
     * Opening time (null if closed)
     */
    @Column(name = "open_time")
    var openTime: LocalTime? = null,

    /**
     * Closing time (null if closed)
     */
    @Column(name = "close_time")
    var closeTime: LocalTime? = null,

    /**
     * Flag indicating if venue is closed on this day
     */
    @Column(nullable = false)
    var isClosed: Boolean = false
) {
    /**
     * Check if venue is currently open (based on current time)
     */
    fun isOpenAt(time: LocalTime): Boolean {
        if (isClosed) return false
        if (openTime == null || closeTime == null) return false

        return time.isAfter(openTime) && time.isBefore(closeTime)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VenueSchedule

        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "VenueSchedule(id=$id, dayOfWeek=$dayOfWeek, openTime=$openTime, closeTime=$closeTime, isClosed=$isClosed)"
    }
}

