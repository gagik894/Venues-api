package app.venues.seating.api

import app.venues.seating.api.dto.*
import java.util.*

interface SeatingApi {
    // --- Chart Lookups ---
    fun getChartInfo(chartId: UUID): SeatingChartInfoDto?
    fun getChartInfoByExternalId(externalId: String): SeatingChartInfoDto?
    fun chartExists(chartId: UUID): Boolean
    fun getSeatingChartName(chartId: UUID): String

    // --- Seat Lookups ---
    fun getSeatInfo(seatId: Long): SeatInfoDto?
    fun getSeatInfoByCode(code: String): SeatInfoDto?
    fun getSeatInfoBatch(seatIds: List<Long>): List<SeatInfoDto>
    fun seatExists(seatId: Long): Boolean

    // --- Container Lookups (Strictly Typed) ---

    /**
     * Get details for a standard Section/Zone.
     */
    fun getSectionInfo(sectionId: Long): SectionInfoDto?

    /**
     * Get details for a Physical Table.
     */
    fun getTableInfo(tableId: Long): TableInfoDto?

    /**
     * Get details for a General Admission Area.
     */
    fun getGaInfo(gaId: Long): GaInfoDto?

    // --- Hierarchy & Logic ---

    /**
     * If a seat belongs to a Table, return that Table's info.
     * Used by Booking logic to handle "Whole Table" pricing.
     */
    fun getTableForSeat(seatId: Long): TableInfoDto?

    /**
     * Get the full structural tree for the Frontend Renderer.
     */
    fun getChartStructure(chartId: UUID): SeatingChartStructureDto?
}