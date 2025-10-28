package app.venues.booking.repository

import app.venues.booking.domain.BookingItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for BookingItem entity operations.
 */
@Repository
interface BookingItemRepository : JpaRepository<BookingItem, Long> {

    /**
     * Find items by booking ID
     */
    fun findByBookingId(bookingId: UUID): List<BookingItem>
}

