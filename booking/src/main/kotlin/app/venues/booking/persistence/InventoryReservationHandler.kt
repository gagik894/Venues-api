package app.venues.booking.persistence

import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatReleasedEvent
import app.venues.common.exception.VenuesException
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

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
     * Also blocks any tables containing this seat to maintain consistency.
     * All operations happen atomically within the same transaction.
     *
     * @throws VenuesException.ResourceConflict if seat unavailable
     * @throws VenuesException.ValidationFailure if seat not priced
     */
    fun reserveSeat(sessionId: UUID, seatId: Long): SeatReservationResult {
        // Atomic operation: reserve + get price in single UPDATE
        val price = sessionSeatConfigRepository.reserveSeatAndGetPrice(sessionId, seatId)
            ?: throw VenuesException.ResourceConflict(
                "Seat is not available for reservation or not priced"
            )

        // Block any tables containing this seat (same transaction for consistency)
        // This prevents tables from being booked when individual seats are reserved
        blockTablesContainingSeat(sessionId, seatId)

        return SeatReservationResult(seatId = seatId, price = price)
    }

    /**
     * Atomically reserve GA tickets and get unit price in a single operation.
     * Thread-safe and prevents race conditions.
     *
     * @throws VenuesException.ResourceConflict if insufficient capacity
     * @throws VenuesException.ValidationFailure if level not priced
     */
    fun reserveGATickets(sessionId: UUID, levelId: Long, quantity: Int): GAReservationResult {
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

    /**
     * Atomically adjust GA ticket reservation.
     * Calculates the delta and applies it to the sold count.
     *
     * @throws VenuesException.ResourceConflict if adjustment fails (e.g., not enough capacity)
     */
    fun adjustGATickets(sessionId: UUID, levelId: Long, newQuantity: Int, oldQuantity: Int) {
        val quantityDelta = newQuantity - oldQuantity

        if (quantityDelta == 0) {
            logger.debug { "No change in GA quantity for level $levelId" }
            return
        }

        val rowsAffected = sessionLevelConfigRepository.adjustGATickets(sessionId, levelId, quantityDelta)

        if (rowsAffected == 0) {
            if (quantityDelta > 0) {
                throw VenuesException.ResourceConflict(
                    "Not enough tickets available to increase quantity. Requested: $quantityDelta"
                )
            } else {
                // This shouldn't happen if data is consistent, but good to log
                logger.warn { "Failed to release $quantityDelta tickets for level $levelId" }
                throw VenuesException.ResourceConflict("Failed to update ticket quantity")
            }
        }

        logger.info { "Adjusted GA tickets for level $levelId by $quantityDelta (new total: $newQuantity)" }
    }

    /**
     * Release a seat reservation.
     * Unblocks tables if all their seats become available.
     */
    fun releaseSeat(sessionId: UUID, seatId: Long) {
        sessionSeatConfigRepository.findBySessionIdAndSeatId(sessionId, seatId)
            ?.let { config ->
                config.release()
                sessionSeatConfigRepository.save(config)

                // Unblock tables containing this seat if all their seats are now available
                unblockTablesIfAllSeatsAvailable(sessionId, seatId)

                // Publish seat released event
                val seatInfo = seatingApi.getSeatInfo(seatId)

                if (seatInfo != null) {
                    eventPublisher.publishEvent(
                        SeatReleasedEvent(
                            sessionId = sessionId,
                            seatIdentifier = seatInfo.code,
                        )
                    )
                }
            }
    }

    /**
     * Release GA tickets back to inventory.
     */
    fun releaseGATickets(sessionId: UUID, levelId: Long, quantity: Int) {
        sessionLevelConfigRepository.findBySessionIdAndLevelId(sessionId, levelId)
            ?.let { config ->
                config.sell(maxOf(0, config.soldCount - quantity))
                sessionLevelConfigRepository.save(config)

                // Publish GA availability changed event
                val gaInfo = seatingApi.getGaInfo(levelId)
                if (gaInfo != null) {
                    val capacity = config.capacity ?: 0
                    val availableTickets = capacity - config.soldCount

                    eventPublisher.publishEvent(
                        GAAvailabilityChangedEvent(
                            sessionId = sessionId,
                            levelIdentifier = gaInfo.code,
                            levelName = gaInfo.name,
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
     *
     * Runs in same transaction as seat reservation for consistency.
     * If this fails, seat reservation will be rolled back.
     */
    private fun blockTablesContainingSeat(sessionId: UUID, seatId: Long) {
        // Get the table that contains this seat (if any)
        val tableInfo = seatingApi.getTableForSeat(seatId) ?: return

        val blockedRows = sessionTableConfigRepository.blockTable(sessionId, tableInfo.id)
        if (blockedRows > 0) {
            logger.debug { "Blocked table ${tableInfo.tableNumber} (ID: ${tableInfo.id}) due to seat $seatId reservation" }
        }
    }

    /**
     * Unblock tables if ALL their seats are now available.
     * Checks all seats in the table - only unblocks if every seat is AVAILABLE.
     *
     * Runs in same transaction as seat release for consistency.
     * If this fails, seat release will be rolled back.
     */
    private fun unblockTablesIfAllSeatsAvailable(sessionId: UUID, seatId: Long) {
        // 1. Find which table this seat belongs to (if any)
        val tableInfo = seatingApi.getTableForSeat(seatId) ?: return

        // 2. Get all seats for this table from seating API
        val tableSeats = seatingApi.getSeatsForTable(tableInfo.id)
        if (tableSeats.isEmpty()) return

        val tableSeatIds = tableSeats.map { it.id }

        // 3. Call the single, atomic repository method to check and update
        val unblockedRows = sessionTableConfigRepository.unblockTableIfAllSeatsAreAvailable(
            sessionId,
            tableInfo.id,
            tableSeatIds
        )

        if (unblockedRows > 0) {
            logger.debug { "Unblocked table ${tableInfo.tableNumber} (ID: ${tableInfo.id}) - all seats available" }
        }
    }
}

