package app.venues.booking.repository

import app.venues.booking.api.domain.BookingStatus
import app.venues.booking.domain.Booking
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for Booking entity operations.
 */
@Repository
interface BookingRepository : JpaRepository<Booking, UUID> {

    /**
     * Find bookings by user
     */
    fun findByUserId(userId: UUID, pageable: Pageable): Page<Booking>

    /**
     * Find bookings by guest
     */
    fun findByGuestId(guestId: UUID, pageable: Pageable): Page<Booking>

    /**
     * Find bookings by user and status
     */
    fun findByUserIdAndStatus(userId: UUID, status: BookingStatus, pageable: Pageable): Page<Booking>

    /**
     * Find bookings by session
     */
    fun findBySessionId(sessionId: UUID, pageable: Pageable): Page<Booking>

    /**
     * Find expired pending bookings.
     */
    fun findByStatusAndCreatedAtBefore(status: BookingStatus, cutoff: Instant): List<Booking>

    // ===========================================
    // STAFF/VENUE QUERY METHODS
    // ===========================================

    /**
     * Find all bookings for a venue (paginated).
     */
    fun findByVenueId(venueId: UUID, pageable: Pageable): Page<Booking>

    /**
     * Find bookings for a venue with specific status.
     */
    fun findByVenueIdAndStatus(venueId: UUID, status: BookingStatus, pageable: Pageable): Page<Booking>

    /**
     * Find bookings by session ID and venue ID (for staff validation).
     */
    fun findBySessionIdAndVenueId(sessionId: UUID, venueId: UUID, pageable: Pageable): Page<Booking>

    /**
     * Find bookings for multiple sessions (for event-level queries).
     */
    @Query("SELECT b FROM Booking b WHERE b.sessionId IN :sessionIds ORDER BY b.createdAt DESC")
    fun findBySessionIdIn(sessionIds: List<UUID>, pageable: Pageable): Page<Booking>

    /**
     * Find bookings for multiple sessions and specific venue (for staff validation).
     */
    @Query("SELECT b FROM Booking b WHERE b.sessionId IN :sessionIds AND b.venueId = :venueId ORDER BY b.createdAt DESC")
    fun findBySessionIdInAndVenueId(sessionIds: List<UUID>, venueId: UUID, pageable: Pageable): Page<Booking>
}

