package app.venues.seating.api

import app.venues.seating.api.dto.LevelInfoDto
import app.venues.seating.api.dto.SeatInfoDto
import app.venues.seating.api.dto.SeatingChartStructureDto

/**
 * Public API contract for Seating module.
 *
 * This is the Port in Hexagonal Architecture.
 * Defines the stable interface for seating data access.
 *
 * Implementation is provided by SeatingService in the seating module.
 */
interface SeatingApi {

    /**
     * Get seat information by ID.
     */
    fun getSeatInfo(seatId: Long): SeatInfoDto?

    /**
     * Get level information by ID.
     */
    fun getLevelInfo(levelId: Long): LevelInfoDto?

    /**
     * Get seating chart name by ID.
     */
    fun getSeatingChartName(chartId: Long): String?

    /**
     * Get complete seating chart structure.
     *
     * Returns all structural information needed to render the seating chart:
     * - Chart metadata (name, configuration)
     * - All levels with hierarchy information
     * - All seats with positions and level associations
     *
     * This is optimized for bulk loading to avoid N+1 queries.
     *
     * @param chartId Seating chart ID
     * @return Complete chart structure or null if not found
     */
    fun getChartStructure(chartId: Long): SeatingChartStructureDto?

    /**
     * Check if seat exists.
     */
    fun seatExists(seatId: Long): Boolean

    /**
     * Check if level exists.
     */
    fun levelExists(levelId: Long): Boolean
}

