package app.venues.booking.repository

import app.venues.booking.domain.Guest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for Guest entity operations.
 */
@Repository
interface GuestRepository : JpaRepository<Guest, Long> {

    /**
     * Find guest by email
     */
    fun findByEmailIgnoreCase(email: String): Guest?

    /**
     * Check if guest exists by email
     */
    fun existsByEmailIgnoreCase(email: String): Boolean
}

