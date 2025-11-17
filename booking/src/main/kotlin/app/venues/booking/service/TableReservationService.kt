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
    fun reserveTable(cart: Cart, sessionId: UUID, tableIdentifier: String): TableReservationResult {
        logger.debug { "Reserving table $tableIdentifier for session $sessionId" }

        // Get table level info (Table is a type of level)
        val tableInfo = seatingApi.getLevelInfoByIdentifier(tableIdentifier)
            ?: throw VenuesException.ResourceNotFound("Table not found: $tableIdentifier")

        val tableId = tableInfo.id
        val tableConfig = sessionTableConfigRepository.findBySessionIdAndTableId(sessionId, tableId)
            ?: throw VenuesException.ValidationFailure(
                "Table '${tableInfo.levelName}' is not configured for this session."
            )

        // Get the *actual* seats for this table (THIS IS THE SOURCE OF TRUTH)
        val seats = seatingApi.getSeatsForLevel(tableId)
        val seatCount = seats.size

        // Validate the seat count.
        if (seatCount <= 0) {
            throw VenuesException.ValidationFailure(
                "Table '${tableInfo.levelName}' (ID: $tableId) has no seats assigned and cannot be booked."
            )
        }

        // Validate table booking mode using the "smart" DTO field
        if (tableConfig.bookingMode == TableBookingMode.SEATS_ONLY) {
            throw VenuesException.ValidationFailure(
                "Table '${tableInfo.levelName}' is configured for SEATS_ONLY booking in this session."
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

        // Block all individual seats (pass the list to avoid a 2nd API call)
        blockAllSeatsInTable(sessionId, tableId, seats)

        // Publish table reserved event
        eventPublisher.publishEvent(
            TableReservedEvent(
                sessionId = sessionId,
                tableId = tableId,
                tableName = tableInfo.levelName
            )
        )

        logger.info { "Table reserved successfully: tableId=$tableId, sessionId=$sessionId" }

        //Return the correct seatCount
        return TableReservationResult(
            tableId = tableId,
            tableName = tableInfo.levelName,
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

        val tableConfig = sessionTableConfigRepository.findBySessionIdAndTableId(sessionId, tableId)
            ?: return

        // Only release if it's RESERVED
        // A SOLD table should not be released by this flow.
        if (tableConfig.status != ConfigStatus.RESERVED) {
            logger.warn { "Attempted to release table $tableId with status ${tableConfig.status}" }
            // If it's already AVAILABLE, still try to unblock seats
            if (tableConfig.status != ConfigStatus.AVAILABLE) return
        }

        // Update table status to AVAILABLE
        tableConfig.release()
        sessionTableConfigRepository.save(tableConfig)

        val tableInfo = seatingApi.getLevelInfo(tableId)

        if (tableConfig.bookingMode != TableBookingMode.TABLE_ONLY) {
            unblockAllSeatsInTable(sessionId, tableId)
        }

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
     * Overloaded method that accepts the already-fetched list of seats.
     */
    private fun blockAllSeatsInTable(sessionId: UUID, tableId: Long, seats: List<SeatInfoDto>) {
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
     * This version fetches the seats itself, as it's called from `releaseTable`.
     */
    private fun unblockAllSeatsInTable(sessionId: UUID, tableId: Long) {
        val seats = seatingApi.getSeatsForLevel(tableId)
        if (seats.isEmpty()) {
            return
        }

        val seatIds = seats.map { it.id }
        val unblockedCount = sessionSeatConfigRepository.unblockSeats(sessionId, seatIds)

        logger.debug { "Unblocked $unblockedCount/${seatIds.size} seats in table $tableId" }
    }
}