package app.venues.event.api

import app.venues.event.api.dto.EventSessionDto
import app.venues.event.api.dto.GaAvailabilityDto
import java.math.BigDecimal
import java.util.*

/**
 * Port interface for event session inventory and pricing operations.
 *
 * Provides atomic seat, GA (general admission), and table reservation methods, as well as price template lookups.
 * Intended for use by other modules via dependency injection; do not access implementation classes directly.
 *
 * All reservation methods are expected to be atomic and concurrency-safe. Implementations must ensure that
 * seat/GA/table availability is enforced at the database level to prevent race conditions.
 *
 * Exceptions:
 * - Throws [app.venues.common.VenuesException.ResourceConflict] if a reservation cannot be made due to unavailability.
 * - Throws [app.venues.common.VenuesException.ResourceNotFound] if the session or resource does not exist.
 * - Throws [app.venues.common.VenuesException.ValidationFailure] for invalid input.
 *
 * Usage example:
 * ```
 * val price = eventApi.reserveSeat(sessionId, seatId)
 * ```
 */
interface EventApi {
    /**
     * Returns session and inventory details for the given event session.
     *
     * @param sessionId Unique identifier for the event session.
     * @return [EventSessionDto] with session details, or null if not found.
     */
    fun getEventSessionInfo(sessionId: UUID): EventSessionDto?

    /**
     * Returns all session IDs for a given event.
     * Used for event-level booking queries.
     *
     * @param eventId Event UUID.
     * @return List of session UUIDs for the event.
     */
    fun getSessionIdsForEvent(eventId: UUID): List<UUID>

    // Seat Reservation

    /**
     * Atomically reserves a seat for the given session.
     *
     * @param sessionId Event session ID.
     * @param seatId Unique seat ID.
     * @return Unit price (snapshot) for the reserved seat, or null if not available.
     * @throws app.venues.common.VenuesException.ResourceConflict if the seat is not available.
     */
    fun reserveSeat(sessionId: UUID, seatId: Long): BigDecimal?

    /**
     * Releases a previously reserved seat, making it available again.
     *
     * @param sessionId Event session ID.
     * @param seatId Unique seat ID.
     */
    fun releaseSeat(sessionId: UUID, seatId: Long)

    /**
     * Releases multiple reserved seats in a batch operation.
     *
     * @param sessionId Event session ID.
     * @param seatIds List of seat IDs to release.
     */
    fun releaseSeatsBatch(sessionId: UUID, seatIds: List<Long>)

    // GA Reservation

    /**
     * Atomically reserves a quantity of GA (general admission) spots in the specified area.
     *
     * @param sessionId Event session ID.
     * @param gaAreaId GA area ID.
     * @param quantity Number of spots to reserve.
     * @return Unit price (snapshot) for the reserved GA area, or null if not available.
     * @throws app.venues.common.VenuesException.ResourceConflict if insufficient capacity.
     */
    fun reserveGa(sessionId: UUID, gaAreaId: Long, quantity: Int): BigDecimal?

    /**
     * Adjusts the reserved GA quantity by the specified delta (positive or negative).
     *
     * @param sessionId Event session ID.
     * @param gaAreaId GA area ID.
     * @param quantityDelta Change in reserved quantity (positive to increase, negative to decrease).
     * @return True if adjustment succeeded, false if insufficient capacity.
     * @throws app.venues.common.VenuesException.ResourceConflict if adjustment cannot be made.
     */
    fun adjustGa(sessionId: UUID, gaAreaId: Long, quantityDelta: Int): Boolean

    /**
     * Releases a quantity of reserved GA spots in the specified area.
     *
     * @param sessionId Event session ID.
     * @param gaAreaId GA area ID.
     * @param quantity Number of spots to release.
     */
    fun releaseGa(sessionId: UUID, gaAreaId: Long, quantity: Int)

    /**
     * Releases reserved GA spots in batch for multiple areas.
     *
     * @param sessionId Event session ID.
     * @param gaAreaQuantities Map of GA area IDs to quantities to release.
     */
    fun releaseGaBatch(sessionId: UUID, gaAreaQuantities: Map<Long, Int>)

    /**
     * Returns current GA availability for the specified area.
     *
     * @param sessionId Event session ID.
     * @param gaAreaId GA area ID.
     * @return [GaAvailabilityDto] with capacity and sold count, or null if not found.
     */
    fun getGaAvailability(sessionId: UUID, gaAreaId: Long): GaAvailabilityDto?

    // Table Reservation

    /**
     * Atomically reserves a table for the given session.
     *
     * @param sessionId Event session ID.
     * @param tableId Table ID.
     * @return Unit price (snapshot) for the reserved table, or null if not available.
     * @throws app.venues.common.VenuesException.ResourceConflict if the table is not available.
     */
    fun reserveTable(sessionId: UUID, tableId: Long): BigDecimal?

    /**
     * Releases a previously reserved table.
     *
     * @param sessionId Event session ID.
     * @param tableId Table ID.
     */
    fun releaseTable(sessionId: UUID, tableId: Long)

    /**
     * Releases multiple reserved tables in a batch operation.
     *
     * @param sessionId Event session ID.
     * @param tableIds List of table IDs to release.
     */
    fun releaseTablesBatch(sessionId: UUID, tableIds: List<Long>)

    /**
     * Returns the booking mode for the specified table (e.g., "FULL", "PARTIAL").
     *
     * @param sessionId Event session ID.
     * @param tableId Table ID.
     * @return Table booking mode name, or null if not found.
     */
    fun getTableBookingMode(sessionId: UUID, tableId: Long): String?

    // Cross-resource blocking (Seat <-> Table)

    /**
     * Blocks the specified seats to prevent table reservation conflicts.
     *
     * @param sessionId Event session ID.
     * @param seatIds List of seat IDs to block.
     * @return Number of seats successfully blocked.
     */
    fun blockSeats(sessionId: UUID, seatIds: List<Long>): Int

    /**
     * Unblocks the specified seats.
     *
     * @param sessionId Event session ID.
     * @param seatIds List of seat IDs to unblock.
     * @return Number of seats successfully unblocked.
     */
    fun unblockSeats(sessionId: UUID, seatIds: List<Long>): Int

    /**
     * Blocks the specified table to prevent seat reservation conflicts.
     *
     * @param sessionId Event session ID.
     * @param tableId Table ID.
     * @return Number of tables successfully blocked (0 or 1).
     */
    fun blockTable(sessionId: UUID, tableId: Long): Int

    /**
     * Unblocks the table if all associated seats are available.
     *
     * @param sessionId Event session ID.
     * @param tableId Table ID.
     * @param seatIds List of seat IDs associated with the table.
     * @return Number of tables unblocked (0 or 1).
     */
    fun unblockTableIfAllSeatsAvailable(sessionId: UUID, tableId: Long, seatIds: List<Long>): Int

    // Price Template Info

    /**
     * Returns the price template names for the specified seats.
     *
     * @param sessionId Event session ID.
     * @param seatIds List of seat IDs.
     * @return Map of seat ID to price template name (nullable).
     */
    fun getSeatPriceTemplateNames(sessionId: UUID, seatIds: List<Long>): Map<Long, String?>

    /**
     * Returns the price template names for the specified GA areas.
     *
     * @param sessionId Event session ID.
     * @param gaAreaIds List of GA area IDs.
     * @return Map of GA area ID to price template name (nullable).
     */
    fun getGaPriceTemplateNames(sessionId: UUID, gaAreaIds: List<Long>): Map<Long, String?>

    /**
     * Returns the price template names for the specified tables.
     *
     * @param sessionId Event session ID.
     * @param tableIds List of table IDs.
     * @return Map of table ID to price template name (nullable).
     */
    fun getTablePriceTemplateNames(sessionId: UUID, tableIds: List<Long>): Map<Long, String?>

    // Sale Confirmation (Reserved -> Sold)

    /**
     * Marks a reserved seat as SOLD.
     *
     * @param sessionId Event session ID.
     * @param seatId Unique seat ID.
     * @throws app.venues.common.VenuesException.ResourceConflict if seat is not RESERVED.
     */
    fun sellSeat(sessionId: UUID, seatId: Long)

    /**
     * Marks multiple reserved seats as SOLD in a batch operation.
     *
     * @param sessionId Event session ID.
     * @param seatIds List of seat IDs to sell.
     */
    fun sellSeatsBatch(sessionId: UUID, seatIds: List<Long>)

    /**
     * Marks reserved GA spots as SOLD.
     *
     * @param sessionId Event session ID.
     * @param gaAreaId GA area ID.
     * @param quantity Number of spots to sell.
     * @throws app.venues.common.VenuesException.ResourceConflict if insufficient RESERVED spots.
     */
    fun sellGa(sessionId: UUID, gaAreaId: Long, quantity: Int)

    /**
     * Marks reserved GA spots as SOLD in batch.
     *
     * @param sessionId Event session ID.
     * @param gaAreaQuantities Map of GA area IDs to quantities to sell.
     */
    fun sellGaBatch(sessionId: UUID, gaAreaQuantities: Map<Long, Int>)

    /**
     * Marks a reserved table as SOLD.
     *
     * @param sessionId Event session ID.
     * @param tableId Table ID.
     * @throws app.venues.common.VenuesException.ResourceConflict if table is not RESERVED.
     */
    fun sellTable(sessionId: UUID, tableId: Long)

    /**
     * Marks multiple reserved tables as SOLD in a batch operation.
     *
     * @param sessionId Event session ID.
     * @param tableIds List of table IDs to sell.
     */
    fun sellTablesBatch(sessionId: UUID, tableIds: List<Long>)

    /**
     * Atomically increments the session-level tickets sold counter.
     *
     * @return true if the increment succeeded, false if capacity would be exceeded.
     */
    fun incrementTicketsSold(sessionId: UUID, quantity: Int): Boolean

    /**
     * Atomically decrements the session-level tickets sold counter.
     *
     * @return true if the decrement succeeded, false if there were not enough sold tickets.
     */
    fun decrementTicketsSold(sessionId: UUID, quantity: Int): Boolean
}
