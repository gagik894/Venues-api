package app.venues.booking.repository

import app.venues.booking.domain.CartSeat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for CartSeat entity operations.
 */
@Repository
interface CartSeatRepository : JpaRepository<CartSeat, Long> {

    /**
     * Find cart seats by reservation token
     */
    fun findByReservationToken(token: UUID): List<CartSeat>

    /**
     * Find expired cart seats
     */
    fun findByExpiresAtBefore(now: Instant): List<CartSeat>

    /**
     * Check if seat is in cart for session
     */
    fun existsBySessionIdAndSeatId(sessionId: Long, seatId: Long): Boolean

    /**
     * Check if seat is in cart for session by identifier
     */
    @Query(
        """
        SELECT COUNT(cs) > 0 FROM CartSeat cs
        WHERE cs.session.id = :sessionId
        AND cs.seat.seatIdentifier = :seatIdentifier
    """
    )
    fun existsBySessionIdAndSeatIdentifier(sessionId: Long, seatIdentifier: String): Boolean

    /**
     * Get reserved seat IDs for session (including expired)
     */
    @Query(
        """
        SELECT cs.seat.id FROM CartSeat cs
        WHERE cs.session.id = :sessionId
    """
    )
    fun findReservedSeatIdsBySession(sessionId: Long): List<Long>

    /**
     * Get active (non-expired) reserved seat IDs for session
     */
    @Query(
        """
        SELECT cs.seat.id FROM CartSeat cs
        WHERE cs.session.id = :sessionId
        AND cs.expiresAt > :now
    """
    )
    fun findActiveReservedSeatIdsBySession(sessionId: Long, now: Instant): List<Long>

    /**
     * Delete by reservation token
     */
    fun deleteByReservationToken(token: UUID)
}

