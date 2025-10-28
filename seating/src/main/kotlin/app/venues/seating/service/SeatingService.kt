package app.venues.seating.service

import app.venues.common.exception.VenuesException
import app.venues.seating.api.dto.*
import app.venues.seating.api.mapper.SeatingMapper
import app.venues.seating.domain.Level
import app.venues.seating.domain.Seat
import app.venues.seating.domain.SeatingChart
import app.venues.seating.repository.LevelRepository
import app.venues.seating.repository.SeatRepository
import app.venues.seating.repository.SeatingChartRepository
import app.venues.venue.repository.VenueRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for seating chart management operations.
 *
 * Handles:
 * - Seating chart CRUD
 * - Level (section) management
 * - Seat management
 * - Translation management
 */
@Service
@Transactional
class SeatingService(
    private val seatingChartRepository: SeatingChartRepository,
    private val levelRepository: LevelRepository,
    private val seatRepository: SeatRepository,
    private val venueRepository: VenueRepository,
    private val seatingMapper: SeatingMapper
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // SEATING CHART OPERATIONS
    // ===========================================

    /**
     * Create a new seating chart for a venue.
     */
    fun createSeatingChart(venueId: Long, request: SeatingChartRequest): SeatingChartResponse {
        logger.debug { "Creating seating chart for venue: $venueId" }

        val venue = venueRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Venue not found with ID: $venueId") }

        // Check for duplicate name
        if (seatingChartRepository.existsByVenueIdAndName(venueId, request.name)) {
            throw VenuesException.ResourceConflict("Seating chart with name '${request.name}' already exists for this venue")
        }

        val chart = SeatingChart(
            venue = venue,
            name = request.name,
            seatIndicatorSize = request.seatIndicatorSize,
            levelIndicatorSize = request.levelIndicatorSize,
            backgroundUrl = request.backgroundUrl
        )

        val savedChart = seatingChartRepository.save(chart)
        logger.info { "Seating chart created successfully: ID=${savedChart.id}" }

        return seatingMapper.toResponse(savedChart)
    }

    /**
     * Get seating chart by ID.
     */
    @Transactional(readOnly = true)
    fun getSeatingChartById(id: Long): SeatingChartResponse {
        logger.debug { "Fetching seating chart: $id" }

        val chart = seatingChartRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $id") }

        return seatingMapper.toResponse(chart)
    }

    /**
     * Get detailed seating chart with levels and seats.
     */
    @Transactional(readOnly = true)
    fun getSeatingChartDetailed(id: Long): SeatingChartDetailedResponse {
        logger.debug { "Fetching detailed seating chart: $id" }

        val chart = seatingChartRepository.findById(id)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $id") }

        return seatingMapper.toDetailedResponse(chart)
    }

    /**
     * Get seating charts by venue.
     */
    @Transactional(readOnly = true)
    fun getSeatingChartsByVenue(venueId: Long, pageable: Pageable): Page<SeatingChartResponse> {
        logger.debug { "Fetching seating charts for venue: $venueId" }
        return seatingChartRepository.findByVenueId(venueId, pageable)
            .map { seatingMapper.toResponse(it) }
    }

    /**
     * Update seating chart.
     */
    fun updateSeatingChart(chartId: Long, venueId: Long, request: SeatingChartRequest): SeatingChartResponse {
        logger.debug { "Updating seating chart: $chartId" }

        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }

        // Verify ownership
        if (chart.venue.id != venueId) {
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

        return seatingMapper.toResponse(savedChart)
    }

    /**
     * Delete seating chart.
     */
    fun deleteSeatingChart(chartId: Long, venueId: Long) {
        logger.debug { "Deleting seating chart: $chartId" }

        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }

        // Verify ownership
        if (chart.venue.id != venueId) {
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

        val chart = seatingChartRepository.findById(chartId)
            .orElseThrow { VenuesException.ResourceNotFound("Seating chart not found with ID: $chartId") }

        // Get parent level if specified
        val parentLevel = request.parentLevelId?.let {
            levelRepository.findById(it)
                .orElseThrow { VenuesException.ResourceNotFound("Parent level not found with ID: $it") }
        }

        val level = Level(
            parentLevel = parentLevel,
            levelName = request.levelName,
            levelIdentifier = request.levelIdentifier,
            levelNumber = request.levelNumber,
            positionX = request.positionX,
            positionY = request.positionY,
            capacity = request.capacity
        )

        val savedLevel = levelRepository.save(level)
        chart.addLevel(savedLevel)
        seatingChartRepository.save(chart)

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
        level.levelNumber = request.levelNumber
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

        val chart = seatingChartRepository.findById(chartId)
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
        chart.addSeat(savedSeat)
        seatingChartRepository.save(chart)

        logger.info { "Seat added successfully: ID=${savedSeat.id}, identifier=${savedSeat.seatIdentifier}" }

        return seatingMapper.toSeatResponse(savedSeat)
    }

    /**
     * Batch add seats to level.
     */
    fun addSeats(chartId: Long, request: BatchSeatRequest): List<SeatResponse> {
        logger.debug { "Batch adding ${request.seats.size} seats to level: ${request.levelId}" }

        val chart = seatingChartRepository.findById(chartId)
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

            val savedSeat = seatRepository.save(seat)
            chart.addSeat(savedSeat)
            savedSeat
        }

        seatingChartRepository.save(chart)
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

