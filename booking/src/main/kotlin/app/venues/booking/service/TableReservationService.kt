package app.venues.booking.service

import app.venues.booking.domain.Cart
import app.venues.booking.event.TableReleasedEvent
import app.venues.booking.event.TableReservedEvent
import app.venues.booking.repository.CartTableRepository
import app.venues.common.exception.VenuesException
import app.venues.event.domain.ConfigStatus
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Service for table booking operations.
 *
 * Handles complete table reservations (booking all seats as a unit).
 * Enforces table booking mode rules and manages table inventory.
 */
@Service
@Transactional
class TableReservationService(
    private val sessionTableConfigRepository: SessionTableConfigRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val cartTableRepository: CartTableRepository,
    private val seatingApi: SeatingApi,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = KotlinLogging.logger {}

    data class TableReservationResult(
        val tableId: Long,
        val tableName: String,
        val seatCount: Int,
        val price: BigDecimal
    )

    /**
     * Reserve a complete table atomically.
     * Blocks all individual seats in the table.
     */
    fun reserveTable(cart: Cart, sessionId: Long, tableId: Long): TableReservationResult {
        logger.debug { "Reserving table $tableId for session $sessionId" }

        // Get table level info
        val tableInfo = seatingApi.getLevelInfo(tableId)
            ?: throw VenuesException.ResourceNotFound("Table not found: $tableId")

        // Validate table booking mode
        if (!canBookAsTable(tableInfo.tableBookingMode)) {
            throw VenuesException.ValidationFailure(
                "Table '${tableInfo.levelName}' can only be booked by individual seats"
            )
        }

        // Check if table already in cart
        if (cartTableRepository.existsByCartAndTableId(cart, tableId)) {
            throw VenuesException.ResourceConflict("Table '${tableInfo.levelName}' is already in your cart")
        }

        // Get table price
        val price = sessionTableConfigRepository.getTablePriceIfAvailable(sessionId, tableId)
            ?: throw VenuesException.ValidationFailure(
                "Table '${tableInfo.levelName}' is not available or not priced"
            )

        // Atomically reserve the table
        val rowsAffected = sessionTableConfigRepository.reserveTableIfAvailable(sessionId, tableId)
        if (rowsAffected == 0) {
            throw VenuesException.ResourceConflict(
                "Table '${tableInfo.levelName}' is not available for reservation"
            )
        }

        // Block all individual seats in the table
        blockAllSeatsInTable(sessionId, tableId)

        // Publish table reserved event
        eventPublisher.publishEvent(
            TableReservedEvent(
                sessionId = sessionId,
                tableId = tableId,
                tableName = tableInfo.levelName
            )
        )

        logger.info { "Table reserved successfully: tableId=$tableId, sessionId=$sessionId" }

        return TableReservationResult(
            tableId = tableId,
            tableName = tableInfo.levelName,
            seatCount = tableInfo.capacity ?: 0,
            price = price
        )
    }

    /**
     * Release a table reservation.
     * Unblocks all individual seats if table booking mode allows it.
     */
    fun releaseTable(sessionId: Long, tableId: Long) {
        logger.debug { "Releasing table $tableId for session $sessionId" }

        val tableConfig = sessionTableConfigRepository.findBySessionIdAndTableId(sessionId, tableId)
            ?: return

        // Update table status to AVAILABLE
        tableConfig.status = ConfigStatus.AVAILABLE
        sessionTableConfigRepository.save(tableConfig)

        // Unblock individual seats if booking mode allows individual booking
        val tableInfo = seatingApi.getLevelInfo(tableId)
        if (tableInfo != null && canBookIndividualSeats(tableInfo.tableBookingMode)) {
            unblockAllSeatsInTable(sessionId, tableId)
        }

        // Publish table released event
        eventPublisher.publishEvent(
            TableReleasedEvent(
                sessionId = sessionId,
                tableId = tableId,
                tableName = tableInfo?.levelName ?: "Table $tableId"
            )
        )

        logger.info { "Table released: tableId=$tableId, sessionId=$sessionId" }
    }

    /**
     * Block all seats in a table atomically (set to BLOCKED status).
     * Uses bulk UPDATE operation for thread-safety under high load.
     */
    private fun blockAllSeatsInTable(sessionId: Long, tableId: Long) {
        val seats = seatingApi.getSeatsForLevel(tableId)
        if (seats.isEmpty()) {
            logger.warn { "Table $tableId has no seats to block" }
            return
        }

        val seatIds = seats.map { it.id }
        val blockedCount = sessionSeatConfigRepository.blockSeats(sessionId, seatIds)

        logger.debug { "Blocked $blockedCount/${seatIds.size} seats in table $tableId" }

        if (blockedCount < seatIds.size) {
            logger.warn {
                "Only blocked $blockedCount/${seatIds.size} seats in table $tableId " +
                        "(some may have been already reserved/sold)"
            }
        }
    }

    /**
     * Unblock all seats in a table atomically (set to AVAILABLE status).
     * Uses bulk UPDATE operation for thread-safety under high load.
     */
    private fun unblockAllSeatsInTable(sessionId: Long, tableId: Long) {
        val seats = seatingApi.getSeatsForLevel(tableId)
        if (seats.isEmpty()) {
            return
        }

        val seatIds = seats.map { it.id }
        val unblockedCount = sessionSeatConfigRepository.unblockSeats(sessionId, seatIds)

        logger.debug { "Unblocked $unblockedCount/${seatIds.size} seats in table $tableId" }
    }

    /**
     * Check if table can be booked as a complete unit.
     */
    private fun canBookAsTable(bookingMode: String?): Boolean {
        return bookingMode == "TABLE_ONLY" || bookingMode == "FLEXIBLE"
    }

    /**
     * Check if individual seats can be booked.
     */
    private fun canBookIndividualSeats(bookingMode: String?): Boolean {
        return bookingMode == "SEATS_ONLY" || bookingMode == "FLEXIBLE"
    }
}

