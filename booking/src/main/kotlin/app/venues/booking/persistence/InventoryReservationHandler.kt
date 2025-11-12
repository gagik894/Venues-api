package app.venues.booking.persistence

import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatReleasedEvent
import app.venues.common.exception.VenuesException
import app.venues.event.domain.ConfigStatus
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val sessionTableConfigRepository: SessionTableConfigRepository,
    private val seatingApi: SeatingApi,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = KotlinLogging.logger {}
    data class SeatReservationResult(
        val seatId: Long,
        val price: BigDecimal
    )

    data class GAReservationResult(
        val levelId: Long,
        val quantity: Int,
        val unitPrice: BigDecimal
    )

    /**
     * Atomically reserve a seat and get its price in a single operation.
     * Thread-safe and prevents race conditions.
     *
     * @throws VenuesException.ResourceConflict if seat unavailable
     * @throws VenuesException.ValidationFailure if seat not priced
     */
    fun reserveSeat(sessionId: Long, seatId: Long): SeatReservationResult {
        // Atomic operation: reserve + get price in single UPDATE
        val price = sessionSeatConfigRepository.reserveSeatAndGetPrice(sessionId, seatId)
            ?: throw VenuesException.ResourceConflict(
                "Seat is not available for reservation or not priced"
            )

        // Block any tables that contain this seat (non-critical, best-effort)
        try {
            blockTablesContainingSeat(sessionId, seatId)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to block tables for seat $seatId (non-critical): ${e.message}" }
        }

        return SeatReservationResult(seatId = seatId, price = price)
    }

    /**
     * Atomically reserve GA tickets and get unit price in a single operation.
     * Thread-safe and prevents race conditions.
     *
     * @throws VenuesException.ResourceConflict if insufficient capacity
     * @throws VenuesException.ValidationFailure if level not priced
     */
    fun reserveGATickets(sessionId: Long, levelId: Long, quantity: Int): GAReservationResult {
        // Atomic operation: reserve + get price in single UPDATE
        val price = sessionLevelConfigRepository.reserveGAAndGetPrice(sessionId, levelId, quantity)
            ?: throw VenuesException.ResourceConflict(
                "Not enough tickets available or level not priced. Requested: $quantity"
            )

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

                // Unblock tables containing this seat if all their seats are now available
                unblockTablesIfAllSeatsAvailable(sessionId, seatId)

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

    /**
     * Block tables that contain the given seat (FLEXIBLE or TABLE_ONLY modes).
     * When any seat is reserved, the table cannot be booked as a unit.
     */
    private fun blockTablesContainingSeat(sessionId: Long, seatId: Long) {
        try {
            val tables = sessionTableConfigRepository.findTablesBySeatId(sessionId, seatId)
            tables.forEach { tableConfig ->
                val blockedRows = sessionTableConfigRepository.blockTable(sessionId, tableConfig.tableId)
                if (blockedRows > 0) {
                    logger.debug { "Blocked table ${tableConfig.tableId} due to seat $seatId reservation" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to block tables for seat $seatId: ${e.message}" }
        }
    }

    /**
     * Unblock tables if ALL their seats are now available.
     * Checks all seats in the table - only unblocks if every seat is AVAILABLE.
     */
    private fun unblockTablesIfAllSeatsAvailable(sessionId: Long, seatId: Long) {
        try {
            val tables = sessionTableConfigRepository.findTablesBySeatId(sessionId, seatId)
            tables.forEach { tableConfig ->
                // Get all seats for this table from seating API
                val tableSeats = seatingApi.getSeatsForLevel(tableConfig.tableId)

                // Check if all seats are available
                val allSeatsAvailable = tableSeats.all { seat ->
                    val seatConfig = sessionSeatConfigRepository.findBySessionIdAndSeatId(
                        sessionId, seat.id ?: return@all false
                    )
                    seatConfig?.status == ConfigStatus.AVAILABLE
                }

                if (allSeatsAvailable) {
                    val unblockedRows = sessionTableConfigRepository.unblockTable(sessionId, tableConfig.tableId)
                    if (unblockedRows > 0) {
                        logger.debug { "Unblocked table ${tableConfig.tableId} - all seats available" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to unblock tables for seat $seatId: ${e.message}" }
        }
    }
}

