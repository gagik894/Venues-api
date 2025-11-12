package app.venues.seating.service

import app.venues.common.exception.VenuesException
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.*
import app.venues.seating.api.mapper.SeatingMapper
import app.venues.seating.domain.Level
import app.venues.seating.domain.Seat
import app.venues.seating.domain.SeatingChart
import app.venues.seating.repository.LevelRepository
import app.venues.seating.repository.SeatRepository
import app.venues.seating.repository.SeatingChartRepository
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for seating chart management operations.
 *
 * This is the ADAPTER in Hexagonal Architecture.
 * Implements SeatingApi (the PORT) to provide a stable public API for other modules.
 *
 * Handles:
 * - Seating chart CRUD
 * - Level (section) management
 * - Seat management
 * - Translation management
 * - Cross-module API (via SeatingApi implementation)
 */
@Service
@Transactional
class SeatingService(
    private val seatingChartRepository: SeatingChartRepository,
    private val levelRepository: LevelRepository,
    private val seatRepository: SeatRepository,
    private val seatingMapper: SeatingMapper,
    private val venueApi: VenueApi
) : SeatingApi {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // PUBLIC API IMPLEMENTATION (SeatingApi Port)
    // ===========================================

    override fun getSeatInfo(seatId: Long): SeatInfoDto? {
        return seatRepository.findById(seatId)
            .map { seat ->
                val level = seat.level
                SeatInfoDto(
                    id = seat.id!!,
                    seatIdentifier = seat.seatIdentifier,
                    seatNumber = seat.seatNumber,
                    rowLabel = seat.rowLabel,
                    levelId = level.id!!,
                    levelName = level.levelName
                )
            }
            .orElse(null)
    }

    override fun getSeatInfoByIdentifier(seatIdentifier: String): SeatInfoDto? {
        val seat = seatRepository.findBySeatIdentifier(seatIdentifier) ?: return null
        val level = seat.level
        return SeatInfoDto(
            id = seat.id!!,
            seatIdentifier = seat.seatIdentifier,
            seatNumber = seat.seatNumber,
            rowLabel = seat.rowLabel,
            levelId = level.id!!,
            levelName = level.levelName
        )
    }

    override fun getLevelInfo(levelId: Long): LevelInfoDto? {
        return levelRepository.findById(levelId)
            .map { level ->
                LevelInfoDto(
                    id = level.id!!,
                    levelName = level.levelName,
                    levelIdentifier = level.levelIdentifier,
                    capacity = level.capacity,
                    isGeneralAdmission = level.isGeneralAdmission(),
                    tableBookingMode = level.tableBookingMode?.name
                )
            }
            .orElse(null)
    }

    override fun getLevelInfoByIdentifier(levelIdentifier: String): LevelInfoDto? {
        val level = levelRepository.findByLevelIdentifier(levelIdentifier) ?: return null
        return LevelInfoDto(
            id = level.id ?: throw IllegalStateException("Level ID should not be null"),
            levelName = level.levelName,
            levelIdentifier = level.levelIdentifier,
            capacity = level.capacity,
            isGeneralAdmission = level.isGeneralAdmission(),
            tableBookingMode = level.tableBookingMode?.name
        )
    }

    override fun getSeatingChartName(chartId: Long): String? {
        return seatingChartRepository.findById(chartId)
            .map { it.name }
            .orElse(null)
    }

    override fun seatExists(seatId: Long): Boolean {
        return seatRepository.existsById(seatId)
    }

    override fun levelExists(levelId: Long): Boolean {
        return levelRepository.existsById(levelId)
    }

    override fun getSeatsForLevel(levelId: Long): List<SeatInfoDto> {
        return seatRepository.findByLevelId(levelId).map { seat ->
            SeatInfoDto(
                id = seat.id!!,
                seatIdentifier = seat.seatIdentifier,
                seatNumber = seat.seatNumber,
                rowLabel = seat.rowLabel,
                levelId = seat.level.id ?: throw IllegalStateException("Level ID should not be null"),
                levelName = seat.level.levelName
            )
        }
    }

    override fun getSeatsForLevelsBatch(levelIds: List<Long>): Map<Long, List<SeatInfoDto>> {
        if (levelIds.isEmpty()) {
            return emptyMap()
        }

        // Single query to load all seats for all levels
        val seats = seatRepository.findByLevelIdIn(levelIds)

        // Group by level ID
        return seats.groupBy { it.level.id!! }
            .mapValues { (_, levelSeats) ->
                levelSeats.map { seat ->
                    SeatInfoDto(
                        id = seat.id!!,
                        seatIdentifier = seat.seatIdentifier,
                        seatNumber = seat.seatNumber,
                        rowLabel = seat.rowLabel,
                        levelId = seat.level.id!!,
                        levelName = seat.level.levelName
                    )
                }
            }
    }

    override fun getChartStructure(chartId: Long): SeatingChartStructureDto? {
        val chart = seatingChartRepository.findById(chartId).orElse(null) ?: return null

        // Load all levels for this chart
        val levels = levelRepository.findBySeatingChartId(chartId)
        val levelDtos = levels.map { level ->
            LevelDto(
                id = level.id ?: throw IllegalStateException("Level ID should not be null"),
                levelName = level.levelName,
                levelIdentifier = level.levelIdentifier,
                parentLevelId = level.parentLevel?.id,
                capacity = level.capacity,
                positionX = level.positionX,
                positionY = level.positionY,
                isTable = level.isTable,
                tableBookingMode = level.tableBookingMode?.name
            )
        }

        // Load all seats for this chart
        val seats = seatRepository.findBySeatingChartId(chartId)
        val seatDtos = seats.map { seat ->
            SeatDto(
                id = seat.id!!,
                seatIdentifier = seat.seatIdentifier,
                seatNumber = seat.seatNumber,
                rowLabel = seat.rowLabel,
                levelId = seat.level.id!!,
                positionX = seat.positionX,
                positionY = seat.positionY,
                seatType = seat.seatType
            )
        }

        return SeatingChartStructureDto(
            chartId = chart.id!!,
            chartName = chart.name,
            levels = levelDtos,
            seats = seatDtos
        )
    }

    // ===========================================
    // SEATING CHART OPERATIONS
    // ===========================================

    /**
     * Create a new seating chart for a venue.
     */
    fun createSeatingChart(venueId: Long, request: SeatingChartRequest): SeatingChartResponse {
        logger.debug { "Creating seating chart for venue: $venueId" }

        // Verify venue exists using VenueApi
        if (!venueApi.venueExists(venueId)) {
            throw VenuesException.ResourceNotFound("Venue not found with ID: $venueId")
        }

        // Check for duplicate name
        if (seatingChartRepository.existsByVenueIdAndName(venueId, request.name)) {
            throw VenuesException.ResourceConflict("Seating chart with name '${request.name}' already exists for this venue")
        }

        val chart = SeatingChart(
            venueId = venueId,
            name = request.name,
            seatIndicatorSize = request.seatIndicatorSize,
            levelIndicatorSize = request.levelIndicatorSize,
            backgroundUrl = request.backgroundUrl
        )

        val savedChart = seatingChartRepository.save(chart)
        logger.info { "Seating chart created successfully: ID=${savedChart.id}" }

        // Fetch venue name via VenueApi
        val venueName = venueApi.getVenueName(venueId)

        // Fetch levels and seats for this chart (service layer responsibility)
        val levels = levelRepository.findBySeatingChartId(savedChart.id!!)
        val seats = seatRepository.findBySeatingChartId(savedChart.id!!)

        return seatingMapper.toResponse(savedChart, levels, seats, venueName = venueName)
    }

    /**
     * Get seating chart by ID.
     */
    @Transactional(readOnly = true)
    fun getSeatingChartById(id: Long): SeatingChartResponse {
        logger.debug { "Fetching seating chart: $id" }

        val chart = seatingChartRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $id") }

        val venueName = venueApi.getVenueName(chart.venueId)

        // Service layer fetches children and aggregates data (proper layered architecture)
        val levels = levelRepository.findBySeatingChartId(id)
        val seats = seatRepository.findBySeatingChartId(id)

        return seatingMapper.toResponse(chart, levels, seats, venueName = venueName)
    }

    /**
     * Get detailed seating chart with levels and seats.
     */
    @Transactional(readOnly = true)
    fun getSeatingChartDetailed(id: Long): SeatingChartDetailedResponse {
        logger.debug { "Fetching detailed seating chart: $id" }

        val chart = seatingChartRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $id") }

        // Service layer aggregates data from repositories
        val levels = levelRepository.findBySeatingChartId(id)
        val seats = seatRepository.findBySeatingChartId(id)
        val venueName = venueApi.getVenueName(chart.venueId)

        return seatingMapper.toDetailedResponse(chart, levels, seats, venueName = venueName)
    }

    /**
     * Get seating charts by venue.
     */
    @Transactional(readOnly = true)
    fun getSeatingChartsByVenue(venueId: Long, pageable: Pageable): Page<SeatingChartResponse> {
        logger.debug { "Fetching seating charts for venue: $venueId" }

        val venueName = venueApi.getVenueName(venueId)

        return seatingChartRepository.findByVenueId(venueId, pageable)
            .map { chart ->
                // Service layer fetches children for each chart
                val levels = levelRepository.findBySeatingChartId(chart.id!!)
                val seats = seatRepository.findBySeatingChartId(chart.id!!)
                seatingMapper.toResponse(chart, levels, seats, venueName = venueName)
            }
    }

    /**
     * Update seating chart.
     */
    fun updateSeatingChart(chartId: Long, venueId: Long, request: SeatingChartRequest): SeatingChartResponse {
        logger.debug { "Updating seating chart: $chartId" }

        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }

        // Verify ownership
        if (chart.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only update your own seating charts")
        }

        // Check for duplicate name (excluding current chart)
        val existing = seatingChartRepository.findByVenueIdAndName(venueId, request.name)
        if (existing != null && existing.id != chartId) {
            throw VenuesException.ResourceConflict("Seating chart with name '${request.name}' already exists")
        }

        chart.name = request.name
        chart.seatIndicatorSize = request.seatIndicatorSize
        chart.levelIndicatorSize = request.levelIndicatorSize
        chart.backgroundUrl = request.backgroundUrl

        val savedChart = seatingChartRepository.save(chart)
        logger.info { "Seating chart updated successfully: $chartId" }

        // Fetch venue name via VenueApi (Hexagonal Architecture)
        val venueName = venueApi.getVenueName(venueId)

        // Service layer fetches children
        val levels = levelRepository.findBySeatingChartId(chartId)
        val seats = seatRepository.findBySeatingChartId(chartId)

        return seatingMapper.toResponse(savedChart, levels, seats, venueName = venueName)
    }

    /**
     * Delete seating chart.
     */
    fun deleteSeatingChart(chartId: Long, venueId: Long) {
        logger.debug { "Deleting seating chart: $chartId" }

        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }

        // Verify ownership
        if (chart.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("You can only delete your own seating charts")
        }

        seatingChartRepository.delete(chart)
        logger.info { "Seating chart deleted successfully: $chartId" }
    }

    // ===========================================
    // LEVEL OPERATIONS
    // ===========================================

    /**
     * Add level to seating chart.
     */
    fun addLevel(chartId: Long, request: LevelRequest): LevelResponse {
        logger.debug { "Adding level to chart: $chartId" }

        seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }

        // Get parent level if specified
        val parentLevel = request.parentLevelId?.let {
            levelRepository.findById(it)
                .orElseThrow { VenuesException.ResourceNotFound("Parent level not found with ID: $it") }
        }

        val level = Level(
            seatingChartId = chartId,
            parentLevel = parentLevel,
            levelName = request.levelName,
            levelIdentifier = request.levelIdentifier,
            positionX = request.positionX,
            positionY = request.positionY,
            capacity = request.capacity
        )

        val savedLevel = levelRepository.save(level)

        logger.info { "Level added successfully: ID=${savedLevel.id}" }

        return seatingMapper.toLevelResponse(savedLevel)
    }

    /**
     * Get level by ID.
     */
    @Transactional(readOnly = true)
    fun getLevelById(id: Long): LevelResponse {
        logger.debug { "Fetching level: $id" }

        val level = levelRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Level not found with ID: $id") }

        return seatingMapper.toLevelResponse(level, includeChildren = true)
    }

    /**
     * Update level.
     */
    fun updateLevel(levelId: Long, request: LevelRequest): LevelResponse {
        logger.debug { "Updating level: $levelId" }

        val level = levelRepository.findById(levelId)
            .orElseThrow { VenuesException.ResourceNotFound("Level not found with ID: $levelId") }

        // Update parent if specified
        if (request.parentLevelId != null) {
            val parentLevel = levelRepository.findById(request.parentLevelId)
                .orElseThrow { VenuesException.ResourceNotFound("Parent level not found with ID: ${request.parentLevelId}") }
            level.parentLevel = parentLevel
        }

        level.levelName = request.levelName
        level.levelIdentifier = request.levelIdentifier
        level.positionX = request.positionX
        level.positionY = request.positionY
        level.capacity = request.capacity

        val savedLevel = levelRepository.save(level)
        logger.info { "Level updated successfully: $levelId" }

        return seatingMapper.toLevelResponse(savedLevel)
    }

    /**
     * Delete level.
     */
    fun deleteLevel(levelId: Long) {
        logger.debug { "Deleting level: $levelId" }

        val level = levelRepository.findById(levelId)
            .orElseThrow { VenuesException.ResourceNotFound("Level not found with ID: $levelId") }

        levelRepository.delete(level)
        logger.info { "Level deleted successfully: $levelId" }
    }

    // ===========================================
    // SEAT OPERATIONS
    // ===========================================

    /**
     * Add seat to level.
     */
    fun addSeat(chartId: Long, request: SeatRequest): SeatResponse {
        logger.debug { "Adding seat to level: ${request.levelId}" }

        // Verify chart exists
        seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }

        val level = levelRepository.findById(request.levelId)
            .orElseThrow { VenuesException.ResourceNotFound("Level not found with ID: ${request.levelId}") }

        // Check for duplicate seat identifier
        if (seatRepository.existsByLevelIdAndSeatIdentifier(request.levelId, request.seatIdentifier)) {
            throw VenuesException.ResourceConflict("Seat with identifier '${request.seatIdentifier}' already exists in this level")
        }

        val seat = Seat(
            level = level,
            seatIdentifier = request.seatIdentifier,
            seatNumber = request.seatNumber,
            rowLabel = request.rowLabel,
            positionX = request.positionX,
            positionY = request.positionY,
            seatType = request.seatType
        )

        val savedSeat = seatRepository.save(seat)

        logger.info { "Seat added successfully: ID=${savedSeat.id}, identifier=${savedSeat.seatIdentifier}" }

        return seatingMapper.toSeatResponse(savedSeat)
    }

    /**
     * Batch add seats to level.
     */
    fun addSeats(chartId: Long, request: BatchSeatRequest): List<SeatResponse> {
        logger.debug { "Batch adding ${request.seats.size} seats to level: ${request.levelId}" }

        // Verify chart exists
        seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }

        val level = levelRepository.findById(request.levelId)
            .orElseThrow { VenuesException.ResourceNotFound("Level not found with ID: ${request.levelId}") }

        val savedSeats = request.seats.map { item ->
            // Check for duplicate
            if (seatRepository.existsByLevelIdAndSeatIdentifier(request.levelId, item.seatIdentifier)) {
                throw VenuesException.ResourceConflict("Seat with identifier '${item.seatIdentifier}' already exists")
            }

            val seat = Seat(
                level = level,
                seatIdentifier = item.seatIdentifier,
                seatNumber = item.seatNumber,
                rowLabel = item.rowLabel,
                positionX = item.positionX,
                positionY = item.positionY,
                seatType = item.seatType
            )

            seatRepository.save(seat)
        }

        logger.info { "Batch added ${savedSeats.size} seats successfully" }

        return savedSeats.map { seatingMapper.toSeatResponse(it) }
    }

    /**
     * Get seat by identifier.
     */
    @Transactional(readOnly = true)
    fun getSeatByIdentifier(levelId: Long, seatIdentifier: String): SeatResponse {
        logger.debug { "Fetching seat: level=$levelId, identifier=$seatIdentifier" }

        val seat = seatRepository.findByLevelIdAndSeatIdentifier(levelId, seatIdentifier)
            ?: throw VenuesException.ResourceNotFound("Seat not found with identifier: $seatIdentifier")

        return seatingMapper.toSeatResponse(seat)
    }

    /**
     * Get seats for a level.
     */
    @Transactional(readOnly = true)
    fun getSeatsByLevel(levelId: Long, pageable: Pageable): Page<SeatResponse> {
        logger.debug { "Fetching seats for level: $levelId" }
        return seatRepository.findByLevelId(levelId, pageable)
            .map { seatingMapper.toSeatResponse(it) }
    }

    /**
     * Delete seat.
     */
    fun deleteSeat(seatId: Long) {
        logger.debug { "Deleting seat: $seatId" }

        val seat = seatRepository.findById(seatId)
            .orElseThrow { VenuesException.ResourceNotFound("Seat not found with ID: $seatId") }

        seatRepository.delete(seat)
        logger.info { "Seat deleted successfully: $seatId" }
    }
}

