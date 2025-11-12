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
     * Get seat information by seat identifier string.
     * Used for API operations that reference seats by their human-readable identifier.
     */
    fun getSeatInfoByIdentifier(seatIdentifier: String): SeatInfoDto?

    /**
     * Get level information by ID.
     */
    fun getLevelInfo(levelId: Long): LevelInfoDto?

    /**
     * Get level information by level identifier string.
     * Used for API operations that reference levels by their human-readable identifier.
     */
    fun getLevelInfoByIdentifier(levelIdentifier: String): LevelInfoDto?

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

    /**
     * Get all seats for a given level.
     * Used for table operations to check if all table seats are available.
     */
    fun getSeatsForLevel(levelId: Long): List<SeatInfoDto>

    /**
     * Get all seats for multiple levels at once (batch operation).
     * Optimized to avoid N+1 queries when loading table seats.
     *
     * @param levelIds List of level IDs
     * @return Map of levelId to list of seats for that level
     */
    fun getSeatsForLevelsBatch(levelIds: List<Long>): Map<Long, List<SeatInfoDto>>
}

