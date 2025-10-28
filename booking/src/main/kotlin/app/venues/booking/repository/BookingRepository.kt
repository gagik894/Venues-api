package app.venues.booking.repository

import app.venues.booking.domain.Booking
import app.venues.booking.domain.BookingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for Booking entity operations.
 */
@Repository
interface BookingRepository : JpaRepository<Booking, UUID> {

    /**
     * Find booking by reservation token
     */
    fun findByReservationToken(reservationToken: UUID): Booking?

    /**
     * Find bookings by user
     */
    fun findByUserId(userId: Long, pageable: Pageable): Page<Booking>

    /**
     * Find bookings by guest
     */
    fun findByGuestId(guestId: Long, pageable: Pageable): Page<Booking>

    /**
     * Find bookings by user and status
     */
    fun findByUserIdAndStatus(userId: Long, status: BookingStatus, pageable: Pageable): Page<Booking>

    /**
     * Find bookings by session
     */
    fun findBySessionId(sessionId: Long, pageable: Pageable): Page<Booking>
}

