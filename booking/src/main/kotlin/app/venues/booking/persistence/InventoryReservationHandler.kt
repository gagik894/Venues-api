package app.venues.booking.persistence

import app.venues.common.exception.VenuesException
import app.venues.event.domain.ConfigStatus
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Performs atomic inventory reservation operations.
 * All operations are idempotent and thread-safe via database constraints.
 */
@Component
class InventoryReservationHandler(
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionLevelConfigRepository: SessionLevelConfigRepository
) {
    data class SeatReservationResult(
        val seatId: Long,
        val price: BigDecimal
    )

    data class GAReservationResult(
        val levelId: Long,
        val quantity: Int,
        val unitPrice: BigDecimal
    )

    fun reserveSeat(sessionId: Long, seatId: Long): SeatReservationResult {
        val price = sessionSeatConfigRepository.getSeatPriceIfAvailable(sessionId, seatId)
            ?: throw VenuesException.ValidationFailure(
                "Seat is not available or not priced for this session"
            )

        val rowsAffected = sessionSeatConfigRepository.reserveSeatIfAvailable(sessionId, seatId)

        if (rowsAffected == 0) {
            throw VenuesException.ResourceConflict("Seat is not available for reservation")
        }

        return SeatReservationResult(seatId = seatId, price = price)
    }

    fun reserveGATickets(sessionId: Long, levelId: Long, quantity: Int): GAReservationResult {
        val price = sessionLevelConfigRepository.getGAPriceIfAvailable(sessionId, levelId, quantity)
            ?: throw VenuesException.ValidationFailure(
                "GA level is not available, not priced, or insufficient capacity for $quantity tickets"
            )

        val rowsAffected = sessionLevelConfigRepository.reserveGATicketsIfAvailable(
            sessionId,
            levelId,
            quantity
        )

        if (rowsAffected == 0) {
            throw VenuesException.ResourceConflict(
                "Not enough tickets available. Requested: $quantity"
            )
        }

        return GAReservationResult(
            levelId = levelId,
            quantity = quantity,
            unitPrice = price
        )
    }

    fun releaseSeat(sessionId: Long, seatId: Long) {
        sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, seatId)
            ?.let { config ->
                config.status = ConfigStatus.AVAILABLE
                sessionSeatConfigRepository.save(config)
            }
    }

    fun releaseGATickets(sessionId: Long, levelId: Long, quantity: Int) {
        sessionLevelConfigRepository.findBySessionIdAndLevelId(sessionId, levelId)
            ?.let { config ->
                config.soldCount = maxOf(0, config.soldCount - quantity)
                sessionLevelConfigRepository.save(config)
            }
    }
}

