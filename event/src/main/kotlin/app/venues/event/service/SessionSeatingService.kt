package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.*
import app.venues.event.domain.Event
import app.venues.event.domain.EventSession
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.LevelDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for retrieving session-specific seating charts.
 *
 * Uses SeatingApi for cross-module communication (Hexagonal Architecture).
 * This service has ZERO knowledge of seating module's internal entities or repositories.
 *
 * Optimized for large venues (10k+ seats) by:
 * - Making a single bulk call to SeatingApi.getChartStructure()
 * - Building hierarchy in memory (efficient for read operations)
 * - Lazy loading price templates only when needed
 * - Caching static seating structure
 */
@Service
class SessionSeatingService(
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionLevelConfigRepository: SessionLevelConfigRepository,
    private val sessionTableConfigRepository: SessionTableConfigRepository,
    private val eventSessionRepository: EventSessionRepository,
    private val seatingApi: SeatingApi
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Get complete seating chart for a session with pricing and availability.
     *
     * Returns flat seats array with embedded level hierarchy for easy UI rendering.
     *
     * @param sessionId Event session ID
     * @return Complete seating structure with pricing and status
     */
    @Transactional(readOnly = true)
    fun getSessionSeating(sessionId: UUID): SessionSeatingResponse {
        logger.debug { "Fetching session seating for session: $sessionId" }

        // 1. Get session and validate
        val session = getSessionOrThrow(sessionId)
        val event = session.event
        val seatingChartId = event.seatingChartId
            ?: throw VenuesException.ValidationFailure("Event does not have a seating chart assigned")

        // 2. Fetch complete seating chart structure via SeatingApi (single call - Hexagonal Architecture)
        val chartStructure = seatingApi.getChartStructure(seatingChartId)
            ?: throw VenuesException.ResourceNotFound("Seating chart not found with ID: $seatingChartId")

        // 3. Batch load all seat configs for this session (1 query)
        val seatConfigs = sessionSeatConfigRepository.findBySessionId(sessionId)
        val seatConfigMap = seatConfigs.associateBy { it.seatId }

        // 4. Batch load all level configs for this session (1 query)
        val levelConfigs = sessionLevelConfigRepository.findBySessionId(sessionId)
        val levelConfigMap = levelConfigs.associateBy { it.levelId }

        // 4b. Batch load all table configs for this session (1 query)
        val tableConfigs = sessionTableConfigRepository.findBySessionId(sessionId)
        val tableConfigMap = tableConfigs.associateBy { it.tableId }

        // 5. Get price templates
        val priceTemplates = getPriceTemplatesForSession(event)

        // 6. Build level hierarchy map from DTOs (no entity access)
        val levelHierarchyMap = buildLevelHierarchyMap(chartStructure.levels)

        // 7. Build flat seats array with level hierarchy embedded
        val seats = mutableListOf<SessionSeatResponse>()
        val gaAreas = mutableListOf<SessionGAAreaResponse>()
        val tables = mutableListOf<SessionTableResponse>()

        // Track which seats belong to tables to avoid duplicates
        val tableSeatIds = mutableSetOf<Long>()

        // First pass: identify all table level IDs for batch loading
        val tableLevelIds = chartStructure.levels
            .filter { level ->
                tableConfigMap[level.id] != null && level.isTable == true
            }
            .map { it.id }

        // Batch load all table seats in one query (avoid N+1)
        val tableLevelToSeatsMap = if (tableLevelIds.isNotEmpty()) {
            try {
                seatingApi.getSeatsForLevelsBatch(tableLevelIds)
            } catch (e: Exception) {
                logger.error(e) { "Failed to batch load table seats: ${e.message}" }
                emptyMap()
            }
        } else {
            emptyMap()
        }

        // Process levels from chart structure DTOs
        for (levelDto in chartStructure.levels) {
            val levelId = levelDto.id
            val levelConfig = levelConfigMap[levelId]
            val tableConfig = tableConfigMap[levelId]

            // Check if this is a table level
            if (tableConfig != null && levelDto.isTable == true) {
                try {
                    // Get seats from batch-loaded map
                    val tableSeats = tableLevelToSeatsMap[levelId]

                    // Validate table has seats
                    if (tableSeats == null || tableSeats.isEmpty()) {
                        logger.warn { "Table level $levelId has no seats, skipping" }
                        continue
                    }

                    val seatIdentifiers = tableSeats.map { it.seatIdentifier }

                    // Track table seat IDs to exclude from individual seat array
                    tableSeatIds.addAll(tableSeats.map { it.id })

                    tables.add(
                        SessionTableResponse(
                            tableId = levelId,
                            tableName = levelDto.levelName,
                            tableIdentifier = levelDto.levelIdentifier,
                            levels = levelHierarchyMap[levelId] ?: listOf(levelDto.levelName),
                            positionX = levelDto.positionX,
                            positionY = levelDto.positionY,
                            bookingMode = tableConfig.bookingMode.name,
                            seatCount = tableSeats.size,
                            seatIdentifiers = seatIdentifiers,
                            status = tableConfig.status.name,
                            price = tableConfig.priceTemplate?.price?.toString(),
                            priceTemplateName = tableConfig.priceTemplate?.templateName,
                            priceTemplateColor = tableConfig.priceTemplate?.color
                        )
                    )

                    logger.debug { "Processed table: ${levelDto.levelName} with ${tableSeats.size} seats" }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to process table level $levelId: ${e.message}" }
                    // Continue processing other levels instead of failing completely
                }
            } else if (levelConfig != null && levelConfig.capacity != null) {
                // GA area - use session-specific capacity and sold count
                val sessionCapacity = levelConfig.capacity
                if (sessionCapacity == null || sessionCapacity <= 0) {
                    logger.warn { "GA level $levelId has invalid capacity, skipping" }
                    continue
                }

                val available = levelConfig.getAvailableCapacity() ?: sessionCapacity
                val levelIdentifier = levelDto.levelIdentifier

                if (levelIdentifier == null) {
                    logger.warn { "GA level $levelId missing identifier, skipping" }
                    continue
                }

                gaAreas.add(
                    SessionGAAreaResponse(
                        levelIdentifier = levelIdentifier,
                        levelName = levelDto.levelName,
                        levels = levelHierarchyMap[levelId] ?: listOf(levelDto.levelName),
                        capacity = sessionCapacity,
                        available = available,
                        price = levelConfig.priceTemplate?.price?.toString() ?: "0.00",
                        priceTemplateName = levelConfig.priceTemplate?.templateName
                    )
                )
            } else {
                // Seated level - get all seats from chart structure
                val levelSeats = chartStructure.seats.filter { it.levelId == levelId }
                for (seatDto in levelSeats) {
                    // Skip seats that belong to tables
                    if (seatDto.id in tableSeatIds) {
                        continue
                    }

                    val config = seatConfigMap[seatDto.id]
                    if (config == null) {
                        logger.warn { "Seat config missing for seat ${seatDto.seatIdentifier}, skipping" }
                        continue
                    }

                    seats.add(
                        SessionSeatResponse(
                            seatIdentifier = seatDto.seatIdentifier,
                            seatNumber = seatDto.seatNumber,
                            rowLabel = seatDto.rowLabel,
                            levels = levelHierarchyMap[levelId] ?: listOf(levelDto.levelName),
                            positionX = seatDto.positionX,
                            positionY = seatDto.positionY,
                            status = config.status.name,
                            price = config.priceTemplate?.price?.toString() ?: "0.00",
                            priceTemplateName = config.priceTemplate?.templateName,
                            priceTemplateColor = config.priceTemplate?.color
                        )
                    )
                }
            }
        }

        // 8. Calculate totals (including table seats)
        val totalSeats = seatConfigs.size
        val availableSeats = seatConfigs.count { it.status.name == "AVAILABLE" }
        val soldSeats = session.ticketsSold
        val tableSeatsCount = tableSeatIds.size

        logger.info {
            "Session seating loaded: $totalSeats seats ($availableSeats available), " +
                    "${tables.size} tables ($tableSeatsCount table seats), ${gaAreas.size} GA areas"
        }

        event.id
            ?: throw VenuesException.ValidationFailure("Event ID is null")

        return SessionSeatingResponse(
            sessionId = sessionId,
            eventId = event.id,
            eventTitle = event.title,
            seatingChartId = seatingChartId,
            seatingChartName = chartStructure.chartName,
            priceTemplates = priceTemplates,
            seats = seats,
            gaAreas = gaAreas,
            tables = tables,
            totalSeats = totalSeats,
            availableSeats = availableSeats,
            soldSeats = soldSeats
        )
    }

    /**
     * Build level hierarchy map from LevelDTOs.
     * Returns Map<levelId, List<levelNames>> where list goes from root to leaf.
     *
     * Example: levelId=5 -> ["Orchestra", "Main Floor", "Row A"]
     *
     * Uses memoization to prevent redundant computation for deep hierarchies.
     */
    private fun buildLevelHierarchyMap(allLevels: List<LevelDto>): Map<Long, List<String>> {
        if (allLevels.isEmpty()) {
            return emptyMap()
        }

        val levelMap = allLevels.associateBy { it.id }
        val hierarchyCache = mutableMapOf<Long, List<String>>()

        fun getHierarchy(levelId: Long, visited: Set<Long> = emptySet()): List<String> {
            // Check cache first
            hierarchyCache[levelId]?.let { return it }

            val level = levelMap[levelId] ?: return emptyList()

            // Detect circular reference
            if (levelId in visited) {
                logger.warn { "Circular hierarchy detected for level $levelId" }
                return listOf(level.levelName)
            }

            val hierarchy = if (level.parentLevelId == null) {
                listOf(level.levelName)
            } else {
                val parentHierarchy = getHierarchy(level.parentLevelId!!, visited + levelId)
                if (parentHierarchy.isNotEmpty()) {
                    parentHierarchy + level.levelName
                } else {
                    listOf(level.levelName)
                }
            }

            hierarchyCache[levelId] = hierarchy
            return hierarchy
        }

        // Pre-compute hierarchy for all levels
        allLevels.forEach { level ->
            if (level.id !in hierarchyCache) {
                getHierarchy(level.id)
            }
        }

        return hierarchyCache.toMap()
    }

    /**
     * Get quick availability info without full seating structure.
     * Much faster for large venues.
     */
    @Transactional(readOnly = true)
    fun getSessionAvailability(sessionId: UUID): SeatAvailabilityResponse {
        logger.debug { "Fetching seat availability for session: $sessionId" }

        val session = getSessionOrThrow(sessionId)

        // Optimized query - count only, no entity loading
        val stats = sessionSeatConfigRepository.getAvailabilityStatsRaw(sessionId)
            ?: return SeatAvailabilityResponse(
                sessionId = sessionId,
                totalSeats = 0,
                availableSeats = 0,
                soldSeats = session.ticketsSold,
                reservedSeats = 0,
                availableSeatIdentifiers = emptyList()
            )

        val totalSeats = stats.totalSeats
        val availableSeats = stats.availableSeats
        val reservedSeats = stats.reservedSeats

        // Only include seat identifiers for small venues (< 1000 seats)
        val availableIdentifiers = if (totalSeats < 1000) {
            val availableSeatIds = sessionSeatConfigRepository.findAvailableSeatIdsBySession(sessionId)

            if (availableSeatIds.isEmpty()) {
                emptyList()
            } else {
                seatingApi.getSeatInfoBatch(availableSeatIds)
                    .map { it.seatIdentifier }
                    .sorted()
            }
        } else {
            emptyList()
        }

        return SeatAvailabilityResponse(
            sessionId = sessionId,
            totalSeats = totalSeats.toInt(),
            availableSeats = availableSeats.toInt(),
            soldSeats = session.ticketsSold,
            reservedSeats = reservedSeats.toInt(),
            availableSeatIdentifiers = availableIdentifiers
        )
    }

    /**
     * Get price templates for session (event + session overrides).
     */
    private fun getPriceTemplatesForSession(
        event: Event
    ): List<SessionPriceTemplateResponse> {

        // Get event price templates
        val eventTemplates = event.priceTemplates.map { template ->
            val templateId = template.id
            SessionPriceTemplateResponse(
                id = templateId,
                templateName = template.templateName,
                color = template.color,
                price = template.price.toString(),
                isOverride = false
            )
        }

        // TODO: Add session-specific overrides when implemented
        // For now, just return event templates

        return eventTemplates.sortedBy { it.price }
    }

    /**
     * Get session or throw exception.
     */
    private fun getSessionOrThrow(sessionId: UUID): EventSession {
        return eventSessionRepository.findById(sessionId)
            .orElseThrow {
                VenuesException.ResourceNotFound("Event session not found with ID: $sessionId")
            }
    }
}

