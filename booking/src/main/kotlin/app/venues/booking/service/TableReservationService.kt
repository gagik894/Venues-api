package app.venues.booking.service

import app.venues.booking.domain.Cart
import app.venues.booking.event.TableReleasedEvent
import app.venues.booking.event.TableReservedEvent
import app.venues.booking.repository.CartTableRepository
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.TableBookingMode
import app.venues.seating.api.dto.SeatInfoDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

/**
 * Service for table booking operations.
 *
 * Handles complete table reservations (booking all seats as a unit).
 * Enforces table booking mode rules and manages table inventory.
 */
@Service
@Transactional
class TableReservationService(
    private val eventApi: EventApi,
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
    fun reserveTable(cart: Cart, sessionId: UUID, tableIdentifier: String): TableReservationResult {
        logger.debug { "Reserving table $tableIdentifier for session $sessionId" }

        // Get table info by code
        val tableInfo = seatingApi.getTableInfoByCode(tableIdentifier)
            ?: throw VenuesException.ResourceNotFound("Table not found: $tableIdentifier")

        val tableId = tableInfo.id

        // Validate table booking mode
        val bookingModeName = eventApi.getTableBookingMode(sessionId, tableId)
            ?: throw VenuesException.ValidationFailure(
                "Table '${tableInfo.tableNumber}' is not configured for this session."
            )

        val bookingMode = try {
            TableBookingMode.valueOf(bookingModeName)
        } catch (e: IllegalArgumentException) {
            TableBookingMode.FLEXIBLE // Default fallback
        }

        // Get the seats for this table
        val seats = seatingApi.getSeatsForTable(tableId)
        val seatCount = seats.size

        // Validate the seat count
        if (seatCount <= 0) {
            throw VenuesException.ValidationFailure(
                "Table '${tableInfo.tableNumber}' (ID: $tableId) has no seats assigned and cannot be booked."
            )
        }

        if (bookingMode == TableBookingMode.SEATS_ONLY) {
            throw VenuesException.ValidationFailure(
                "Table '${tableInfo.tableNumber}' is configured for SEATS_ONLY booking in this session."
            )
        }

        // Check if table already in cart
        if (cartTableRepository.existsByCartAndTableId(cart, tableId)) {
            throw VenuesException.ResourceConflict("Table '${tableInfo.tableNumber}' is already in your cart")
        }

        // Atomically reserve the table and get price
        val price = eventApi.reserveTable(sessionId, tableId)
            ?: throw VenuesException.ResourceConflict(
                "Table '${tableInfo.tableNumber}' is not available for reservation or not priced"
            )

        // Block all individual seats
        blockAllSeatsInTable(sessionId, tableId, seats)

        // Publish table reserved event
        eventPublisher.publishEvent(
            TableReservedEvent(
                sessionId = sessionId,
                tableId = tableId,
                tableName = tableInfo.tableNumber,
                tableCode = tableInfo.code
            )
        )

        logger.info { "Table reserved successfully: tableId=$tableId, sessionId=$sessionId" }

        return TableReservationResult(
            tableId = tableId,
            tableName = tableInfo.tableNumber,
            seatCount = seatCount,
            price = price
        )
    }

    /**
     * Release a table reservation.
     * Unblocks all individual seats if table booking mode allows it.
     */
    fun releaseTable(sessionId: UUID, tableId: Long) {
        logger.debug { "Releasing table $tableId for session $sessionId" }

        // We need to check booking mode before releasing to know if we should unblock seats
        val bookingModeName = eventApi.getTableBookingMode(sessionId, tableId)
        val bookingMode = if (bookingModeName != null) {
            try {
                TableBookingMode.valueOf(bookingModeName)
            } catch (e: IllegalArgumentException) {
                TableBookingMode.FLEXIBLE
            }
        } else null

        // Release table
        eventApi.releaseTable(sessionId, tableId)

        if (bookingMode != null && bookingMode != TableBookingMode.TABLE_ONLY) {
            unblockAllSeatsInTable(sessionId, tableId)
        }

        // Get table info for event
        val tableInfo = seatingApi.getTableInfo(tableId)

        eventPublisher.publishEvent(
            TableReleasedEvent(
                sessionId = sessionId,
                tableId = tableId,
                tableName = tableInfo?.tableNumber ?: "Table $tableId",
                tableCode = tableInfo?.code ?: "TABLE-$tableId"
            )
        )

        logger.info { "Table released: tableId=$tableId, sessionId=$sessionId" }
    }

    /**
     * Block all seats in a table atomically (set to BLOCKED status).
     * Overloaded method that accepts the already-fetched list of seats.
     */
    private fun blockAllSeatsInTable(sessionId: UUID, tableId: Long, seats: List<SeatInfoDto>) {
        if (seats.isEmpty()) {
            logger.warn { "Table $tableId has no seats to block" }
            return
        }

        val seatIds = seats.map { it.id }
        val blockedCount = eventApi.blockSeats(sessionId, seatIds)

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
     */
    private fun unblockAllSeatsInTable(sessionId: UUID, tableId: Long) {
        val seats = seatingApi.getSeatsForTable(tableId)
        if (seats.isEmpty()) {
            return
        }

        val seatIds = seats.map { it.id }
        val unblockedCount = eventApi.unblockSeats(sessionId, seatIds)

        logger.debug { "Unblocked $unblockedCount/${seatIds.size} seats in table $tableId" }
    }

    /**
     * BATCH operation: Release multiple tables atomically.
     * Optimized for cart cleanup operations.
     *
     * Performance: Single UPDATE per operation type instead of N queries.
     *
     * @param sessionId Session ID
     * @param tableIds List of table IDs to release
     */
    fun releaseTablesBatch(sessionId: UUID, tableIds: List<Long>) {
        if (tableIds.isEmpty()) return

        logger.debug { "Batch releasing ${tableIds.size} tables for session $sessionId" }

        // 1. Release all tables in one UPDATE
        eventApi.releaseTablesBatch(sessionId, tableIds)

        // 2. Unblock all seats for these tables (batch operation)
        // Get all seat IDs for all tables
        val allSeatIds = tableIds.flatMap { tableId ->
            seatingApi.getSeatsForTable(tableId).map { it.id }
        }

        if (allSeatIds.isNotEmpty()) {
            val unblockedSeats = eventApi.unblockSeats(sessionId, allSeatIds)
            logger.debug { "Unblocked $unblockedSeats seats from ${tableIds.size} tables" }
        }

        logger.info { "Batch released tables with ${allSeatIds.size} seats for session $sessionId" }
    }
}


