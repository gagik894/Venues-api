package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.*
import app.venues.event.domain.Event
import app.venues.event.domain.EventSession
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionLevelConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.seating.domain.Level
import app.venues.seating.repository.LevelRepository
import app.venues.seating.repository.SeatRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for retrieving session-specific seating charts.
 *
 * Optimized for large venues (10k+ seats) by:
 * - Using batch queries to minimize database round trips
 * - Building hierarchy in memory (efficient for read operations)
 * - Lazy loading price templates only when needed
 * - Caching static seating structure
 */
@Service
class SessionSeatingService(
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionLevelConfigRepository: SessionLevelConfigRepository,
    private val eventSessionRepository: EventSessionRepository,
    private val levelRepository: LevelRepository,
    private val seatRepository: SeatRepository
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
    fun getSessionSeating(sessionId: Long): SessionSeatingResponse {
        logger.debug { "Fetching session seating for session: $sessionId" }

        // 1. Get session and validate
        val session = getSessionOrThrow(sessionId)
        val event = session.event
        val seatingChart = event.seatingChart
            ?: throw VenuesException.ValidationFailure("Event does not have a seating chart assigned")

        val seatingChartId = seatingChart.id
            ?: throw VenuesException.ValidationFailure("Seating chart ID is null")

        // 2. Batch load all seat configs for this session (1 query)
        val seatConfigs = sessionSeatConfigRepository.findBySessionId(sessionId)
        val seatConfigMap = seatConfigs.associateBy { it.seat.id }

        // 3. Batch load all level configs for this session (1 query)
        val levelConfigs = sessionLevelConfigRepository.findBySessionId(sessionId)
        val levelConfigMap = levelConfigs.associateBy { it.level.id }

        // 4. Get price templates
        val priceTemplates = getPriceTemplatesForSession(event)

        // 5. Load all levels and build hierarchy map
        val allLevels = levelRepository.findBySeatingChartId(seatingChartId)
        val levelHierarchyMap = buildLevelHierarchyMap(allLevels)

        // 6. Build flat seats array with level hierarchy embedded
        val seats = mutableListOf<SessionSeatResponse>()
        val gaAreas = mutableListOf<SessionGAAreaResponse>()

        for (level in allLevels) {
            val levelId = level.id ?: continue
            val levelConfig = levelConfigMap[levelId]

            // Check if this is a GA level (has capacity in session config)
            val sessionCapacity = levelConfig?.capacity
            if (sessionCapacity != null) {
                // GA area - use session-specific capacity and sold count
                val available = levelConfig.getAvailableCapacity() ?: sessionCapacity

                gaAreas.add(
                    SessionGAAreaResponse(
                        levelName = level.levelName,
                        levels = levelHierarchyMap[levelId] ?: listOf(level.levelName),
                        capacity = sessionCapacity,
                        available = available,
                        price = levelConfig.price.toString(),
                        priceTemplateId = levelConfig.priceTemplate?.id,
                        priceTemplateName = levelConfig.priceTemplate?.templateName
                    )
                )
            } else {
                // Seated level - get all seats
                val levelSeats = seatRepository.findByLevelId(levelId)
                for (seat in levelSeats) {
                    val config = seatConfigMap[seat.id] ?: continue

                    seats.add(
                        SessionSeatResponse(
                            seatIdentifier = seat.seatIdentifier,
                            seatNumber = seat.seatNumber,
                            rowLabel = seat.rowLabel,
                            levels = levelHierarchyMap[levelId] ?: listOf(level.levelName),
                            positionX = seat.positionX,
                            positionY = seat.positionY,
                            status = config.status.name,
                            price = config.price.toString(),
                            priceTemplateId = config.priceTemplate?.id,
                            priceTemplateName = config.priceTemplate?.templateName,
                            priceTemplateColor = config.priceTemplate?.color
                        )
                    )
                }
            }
        }

        // 7. Calculate totals
        val totalSeats = seatConfigs.size
        val availableSeats = seatConfigs.count { it.status.name == "AVAILABLE" }
        val soldSeats = session.ticketsSold

        val eventId = event.id ?: throw VenuesException.ValidationFailure("Event ID is null")

        return SessionSeatingResponse(
            sessionId = sessionId,
            eventId = eventId,
            eventTitle = event.title,
            seatingChartId = seatingChartId,
            seatingChartName = seatingChart.name,
            priceTemplates = priceTemplates,
            seats = seats,
            gaAreas = gaAreas,
            totalSeats = totalSeats,
            availableSeats = availableSeats,
            soldSeats = soldSeats
        )
    }

    /**
     * Build level hierarchy map.
     * Returns Map<levelId, List<levelNames>> where list goes from root to leaf.
     *
     * Example: levelId=5 -> ["Orchestra", "Main Floor", "Row A"]
     */
    private fun buildLevelHierarchyMap(allLevels: List<Level>): Map<Long?, List<String>> {
        val levelMap = allLevels.filter { it.id != null }.associateBy { it.id }
        val hierarchyMap = mutableMapOf<Long?, List<String>>()

        fun getHierarchy(level: Level): List<String> {
            return if (level.parentLevel == null) {
                listOf(level.levelName)
            } else {
                val parentId = level.parentLevel?.id
                val parent = if (parentId != null) levelMap[parentId] else null
                if (parent != null) {
                    getHierarchy(parent) + level.levelName
                } else {
                    listOf(level.levelName)
                }
            }
        }

        for (level in allLevels) {
            hierarchyMap[level.id] = getHierarchy(level)
        }

        return hierarchyMap
    }

    /**
     * Get quick availability info without full seating structure.
     * Much faster for large venues.
     */
    @Transactional(readOnly = true)
    fun getSessionAvailability(sessionId: Long): SeatAvailabilityResponse {
        logger.debug { "Fetching seat availability for session: $sessionId" }

        val session = getSessionOrThrow(sessionId)

        // Optimized query - count only, no entity loading
        val statsArray = sessionSeatConfigRepository.getAvailabilityStatsRaw(sessionId)
        val totalSeats = statsArray[0].toInt()
        val availableSeats = statsArray[1].toInt()
        val reservedSeats = statsArray[2].toInt()

        // Only include seat identifiers for small venues (< 1000 seats)
        val availableIdentifiers = if (totalSeats < 1000) {
            sessionSeatConfigRepository.findAvailableSeatIdentifiers(sessionId)
        } else {
            emptyList()
        }

        return SeatAvailabilityResponse(
            sessionId = sessionId,
            totalSeats = totalSeats,
            availableSeats = availableSeats,
            soldSeats = session.ticketsSold,
            reservedSeats = reservedSeats,
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
            val templateId = template.id ?: return@map null
            SessionPriceTemplateResponse(
                id = templateId,
                templateName = template.templateName,
                color = template.color,
                price = template.price.toString(),
                displayOrder = template.displayOrder,
                isOverride = false
            )
        }.filterNotNull()

        // TODO: Add session-specific overrides when implemented
        // For now, just return event templates

        return eventTemplates.sortedBy { it.displayOrder }
    }

    /**
     * Get session or throw exception.
     */
    private fun getSessionOrThrow(sessionId: Long): EventSession {
        return eventSessionRepository.findById(sessionId)
            .orElseThrow {
                VenuesException.ResourceNotFound("Event session not found with ID: $sessionId")
            }
    }
}

