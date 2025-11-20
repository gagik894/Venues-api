package app.venues.booking.persistence

import app.venues.booking.event.GAAvailabilityChangedEvent
import app.venues.booking.event.SeatReleasedEvent
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
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
    private val eventApi: EventApi,
    private val seatingApi: SeatingApi,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = KotlinLogging.logger {}
    data class SeatReservationResult(
        val seatId: Long,
        val price: BigDecimal
    )

    data class GaReservationResult(
        val gaAreaId: Long,
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
        val price = eventApi.reserveSeat(sessionId, seatId)
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
    fun reserveGATickets(sessionId: UUID, gaAreaId: Long, quantity: Int): GaReservationResult {
        // Atomic operation: reserve + get price in single UPDATE
        val price = eventApi.reserveGa(sessionId, gaAreaId, quantity)
            ?: throw VenuesException.ResourceConflict(
                "Not enough tickets available or level not priced. Requested: $quantity"
            )

        return GaReservationResult(
            gaAreaId = gaAreaId,
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
    fun adjustGATickets(sessionId: UUID, gaAreaId: Long, newQuantity: Int, oldQuantity: Int) {
        val quantityDelta = newQuantity - oldQuantity

        if (quantityDelta == 0) {
            logger.debug { "No change in GA quantity for level $gaAreaId" }
            return
        }

        val success = eventApi.adjustGa(sessionId, gaAreaId, quantityDelta)

        if (!success) {
            if (quantityDelta > 0) {
                throw VenuesException.ResourceConflict(
                    "Not enough tickets available to increase quantity. Requested: $quantityDelta"
                )
            } else {
                // This shouldn't happen if data is consistent, but good to log
                logger.warn { "Failed to release $quantityDelta tickets for level $gaAreaId" }
                throw VenuesException.ResourceConflict("Failed to update ticket quantity")
            }
        }

        logger.info { "Adjusted GA tickets for level $gaAreaId by $quantityDelta (new total: $newQuantity)" }
    }

    /**
     * Release a seat reservation.
     * Unblocks tables if all their seats become available.
     */
    fun releaseSeat(sessionId: UUID, seatId: Long) {
        eventApi.releaseSeat(sessionId, seatId)

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

    /**
     * Release GA tickets back to inventory.
     */
    fun releaseGATickets(sessionId: UUID, gaAreaId: Long, quantity: Int) {
        eventApi.releaseGa(sessionId, gaAreaId, quantity)

        // Publish GA availability changed event
        val gaInfo = seatingApi.getGaInfo(gaAreaId)
        if (gaInfo != null) {
            // Fetch actual GA availability from EventApi before publishing event.
            val gaAvailability = eventApi.getGaAvailability(sessionId, gaAreaId)
            if (gaAvailability != null) {
                eventPublisher.publishEvent(
                    GAAvailabilityChangedEvent(
                        sessionId = sessionId,
                        levelIdentifier = gaInfo.code,
                        levelName = gaInfo.name,
                        availableTickets = gaAvailability.availableTickets,
                        totalCapacity = gaAvailability.totalCapacity
                    )
                )
            }
        }
    }

    /**
     * BATCH operation: Release multiple seats atomically.
     * Optimized for high-volume scenarios (cart cleanup, bulk operations).
     *
     * Performance: O(1) instead of O(n) - single database UPDATE.
     *
     * @param sessionId Session ID
     * @param seatIds List of seat IDs to release
     */
    fun releaseSeatsBatch(sessionId: UUID, seatIds: List<Long>) {
        if (seatIds.isEmpty()) return

        // Single bulk UPDATE query
        eventApi.releaseSeatsBatch(sessionId, seatIds)

        logger.info { "Batch released ${seatIds.size} seats for session $sessionId" }

        // Note: Individual seat released events not published for batch operations
        // to avoid event storm. Consider aggregate event if needed.
    }

    /**
     * BATCH operation: Release GA tickets for multiple areas.
     * Optimized for cart cleanup operations - all in single transaction.
     *
     * @param sessionId Session ID
     * @param gaAreaQuantities List of (gaAreaId, quantity) pairs
     */
    fun releaseGATicketsBatch(sessionId: UUID, gaAreaQuantities: List<Pair<Long, Int>>) {
        if (gaAreaQuantities.isEmpty()) return

        // Process each GA area release (all in same transaction for atomicity)
        val map = gaAreaQuantities.toMap()
        eventApi.releaseGaBatch(sessionId, map)

        logger.info {
            "Batch released GA tickets across ${gaAreaQuantities.size} areas for session $sessionId"
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

        val blockedRows = eventApi.blockTable(sessionId, tableInfo.id)
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
        val unblockedRows = eventApi.unblockTableIfAllSeatsAvailable(
            sessionId,
            tableInfo.id,
            tableSeatIds
        )

        if (unblockedRows > 0) {
            logger.debug { "Unblocked table ${tableInfo.tableNumber} (ID: ${tableInfo.id}) - all seats available" }
        }
    }
}


