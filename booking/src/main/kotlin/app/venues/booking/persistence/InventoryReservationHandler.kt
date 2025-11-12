package app.venues.booking.persistence

import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatReleasedEvent
import app.venues.common.exception.VenuesException
import app.venues.event.domain.ConfigStatus
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.seating.api.SeatingApi
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Performs atomic inventory reservation operations.
 * All operations are idempotent and thread-safe via database constraints.
 * Publishes domain events for inventory changes.
 */
@Component
class InventoryReservationHandler(
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionLevelConfigRepository: SessionLevelConfigRepository,
    private val seatingApi: SeatingApi,
    private val eventPublisher: ApplicationEventPublisher
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

                // Publish seat released event
                val seatInfo = seatingApi.getSeatInfo(seatId)
                val levelInfo = seatInfo?.let { seatingApi.getLevelInfo(it.levelId) }

                if (seatInfo != null && levelInfo != null) {
                    eventPublisher.publishEvent(
                        SeatReleasedEvent(
                            sessionId = sessionId,
                            seatIdentifier = seatInfo.seatIdentifier,
                            levelName = levelInfo.levelName
                        )
                    )
                }
            }
    }

    fun releaseGATickets(sessionId: Long, levelId: Long, quantity: Int) {
        sessionLevelConfigRepository.findBySessionIdAndLevelId(sessionId, levelId)
            ?.let { config ->
                config.soldCount = maxOf(0, config.soldCount - quantity)
                sessionLevelConfigRepository.save(config)

                // Publish GA availability changed event
                val levelInfo = seatingApi.getLevelInfo(levelId)
                if (levelInfo != null) {
                    val capacity = config.capacity ?: 0
                    val availableTickets = capacity - config.soldCount

                    eventPublisher.publishEvent(
                        GAAvailabilityChangedEvent(
                            sessionId = sessionId,
                            levelIdentifier = levelInfo.levelIdentifier ?: "",
                            levelName = levelInfo.levelName,
                            availableTickets = availableTickets,
                            totalCapacity = capacity
                        )
                    )
                }
            }
    }
}

