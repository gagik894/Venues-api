package app.venues.seating.api

import app.venues.seating.api.dto.*
import java.util.*

/**
 * Port interface for seating module.
 * Provides stable API for cross-module communication (booking, event, etc.).
 * All DTOs are immutable and do not expose domain entities.
 */
interface SeatingApi {

    // --- Chart Operations ---

    /**
     * Get basic chart information by ID.
     * @return chart info or null if not found
     */
    fun getChartInfo(chartId: UUID): SeatingChartInfoDto?

    /**
     * Check if chart exists.
     * @return true if chart exists
     */
    fun chartExists(chartId: UUID): Boolean

    /**
     * Get chart name for display purposes.
     * @return chart name or empty string if not found
     */
    fun getSeatingChartName(chartId: UUID): String

    /**
     * Get complete chart structure for frontend rendering.
     * Returns immutable snapshot of zones, tables, seats, and GA areas.
     * @return full structure or null if chart not found
     */
    fun getChartStructure(chartId: UUID): SeatingChartStructureDto?

    // --- Seat Operations ---

    /**
     * Get seat information by ID.
     * @return seat info or null if not found
     */
    fun getSeatInfo(seatId: Long): SeatInfoDto?

    /**
     * Get seat information by business key (code).
     * @return seat info or null if not found
     */
    fun getSeatInfoByCode(code: String): SeatInfoDto?

    /**
     * Batch fetch seat information for multiple seats.
     * @param seatIds list of seat IDs
     * @return list of found seats (missing IDs are omitted)
     */
    fun getSeatInfoBatch(seatIds: List<Long>): List<SeatInfoDto>

    /**
     * Check if seat exists.
     * @return true if seat exists
     */
    fun seatExists(seatId: Long): Boolean

    // --- Container Operations ---

    /**
     * Get section/zone information.
     * @return section info or null if not found
     */
    fun getSectionInfo(sectionId: Long): SectionInfoDto?

    /**
     * Get physical table information.
     * @return table info or null if not found
     */
    fun getTableInfo(tableId: Long): TableInfoDto?

    /**
     * Get general admission area information.
     * @return GA info or null if not found
     */
    fun getGaInfo(gaId: Long): GaInfoDto?

    /**
     * Get general admission area by business key (code).
     * @param code GA area code (e.g., "PIT_A")
     * @return GA info or null if not found
     */
    fun getGaInfoByCode(code: String): GaInfoDto?

    /**
     * Get table that contains a specific seat.
     * Used by booking logic to handle whole-table pricing.
     * @return table info or null if seat has no table or seat not found
     */
    fun getTableForSeat(seatId: Long): TableInfoDto?

    /**
     * Get table information by business key (code).
     * @param code Table code (e.g., "VIP_T12")
     * @return table info or null if not found
     */
    fun getTableInfoByCode(code: String): TableInfoDto?

    /**
     * Get all seats for a specific table.
     * Used by booking logic for table reservations.
     * @param tableId The table ID
     * @return list of seats in the table (empty if table has no seats)
     */
    fun getSeatsForTable(tableId: Long): List<SeatInfoDto>
}
