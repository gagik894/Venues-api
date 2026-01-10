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
     * Get total seat count for a chart (optimized - single COUNT query).
     * Use this instead of getChartStructure when only count is needed.
     * @return seat count or 0 if chart not found
     */
    fun getSeatCount(chartId: UUID): Int

    /**
     * Get complete chart structure for backend operations (admin, session setup).
     * NOTE: For user-facing APIs, use the Split Strategy:
     * - Static: GET /seating-charts/{chartId}/structure (cached)
     * - Dynamic: GET /sessions/{sessionId}/inventory (real-time)
     *
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
     * Get seat information by business key (code) within a specific chart.
     * @param chartId seating chart UUID
     * @param code seat code (e.g., "SEC_A-ROW-1-SEAT-10")
     * @return seat info or null if not found
     */
    fun getSeatInfoByCode(chartId: UUID, code: String): SeatInfoDto?

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
     * Get general admission area by business key (code) within a specific chart.
     * @param chartId seating chart UUID
     * @param code GA area code (e.g., "PIT_A")
     * @return GA info or null if not found
     */
    fun getGaInfoByCode(chartId: UUID, code: String): GaInfoDto?

    /**
     * Get table that contains a specific seat.
     * Used by booking logic to handle whole-table pricing.
     * @return table info or null if seat has no table or seat not found
     */
    fun getTableForSeat(seatId: Long): TableInfoDto?

    /**
     * Get table information by business key (code) within a specific chart.
     * @param chartId seating chart UUID
     * @param code Table code (e.g., "VIP_T12")
     * @return table info or null if not found
     */
    fun getTableInfoByCode(chartId: UUID, code: String): TableInfoDto?

    /**
     * Get all seats for a specific table.
     * Used by booking logic for table reservations.
     * @param tableId The table ID
     * @return list of seats in the table (empty if table has no seats)
     */
    fun getSeatsForTable(tableId: Long): List<SeatInfoDto>

    /**
     * Get the full hierarchy of zones for a given zone ID.
     * Returns a list of SectionInfoDto ordered from Root -> Leaf.
     * @param zoneId The ID of the zone (leaf or intermediate)
     * @return List of zones in the hierarchy
     */
    fun getZoneHierarchy(zoneId: Long): List<SectionInfoDto>

    // --- Location Display Operations ---

    /**
     * Get formatted location lines for a seat (for tickets, emails, PDFs).
     * Returns hierarchy + row + seat, each on separate line for multi-line display.
     * Example: ["Right Tribune", "Sector 5", "Row 3", "Seat 10"]
     * @param seatId The seat ID
     * @param locale The locale for i18n (e.g., "en", "hy", "ru")
     * @return List of location lines, or empty if seat not found
     */
    fun getSeatLocationLines(seatId: Long, locale: String?): List<String>

    /**
     * Get formatted location lines for a GA area (for tickets, emails, PDFs).
     * Returns hierarchy + GA name.
     * Example: ["Main Hall", "Fan Zone"]
     * @param gaAreaId The GA area ID
     * @param locale The locale for i18n
     * @return List of location lines, or empty if GA not found
     */
    fun getGaLocationLines(gaAreaId: Long, locale: String?): List<String>

    /**
     * Get formatted location lines for a table (for tickets, emails, PDFs).
     * Returns hierarchy + table number.
     * Example: ["VIP Section", "Table 5"]
     * @param tableId The table ID
     * @param locale The locale for i18n
     * @return List of location lines, or empty if table not found
     */
    fun getTableLocationLines(tableId: Long, locale: String?): List<String>
}
