package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.*
import app.venues.event.domain.Event
import app.venues.event.domain.EventSession
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import app.venues.seating.api.dto.ZoneDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for managing session-specific seating configurations.
 * Uses SeatingApi for cross-module communication (Ports & Adapters architecture).
 * Zero knowledge of seating module's internal entities.
 *
 * Architecture notes:
 * - Seating module provides: chart structure (zones, seats, tables, GA areas)
 * - Event module provides: session-specific pricing and availability status
 * - This service merges both to create session seating response
 */
@Service
@Transactional
class SessionSeatingService(
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionGAConfigRepository: SessionGAConfigRepository,
    private val sessionTableConfigRepository: SessionTableConfigRepository,
    private val eventSessionRepository: EventSessionRepository,
    private val seatingApi: SeatingApi
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Get complete seating chart for session with pricing and availability.
     * Optimized for large venues (10k+ seats).
     */
    @Transactional(readOnly = true)
    fun getSessionSeating(sessionId: UUID): SessionSeatingResponse {
        logger.debug { "Fetching session seating for session: $sessionId" }

        val session = getSessionOrThrow(sessionId)
        val event = session.event
        val seatingChartId = event.seatingChartId
            ?: throw VenuesException.ValidationFailure("Event does not have a seating chart assigned")

        // Fetch complete chart structure via SeatingApi (single call)
        val chartStructure = seatingApi.getChartStructure(seatingChartId)
            ?: throw VenuesException.ResourceNotFound("Seating chart not found")

        // Batch load all session configs (3 queries total)
        val seatConfigs = sessionSeatConfigRepository.findBySessionId(sessionId)
        val seatConfigMap = seatConfigs.associateBy { it.seatId }

        val gaConfigs = sessionGAConfigRepository.findBySessionId(sessionId)
        val gaConfigMap = gaConfigs.associateBy { it.gaAreaId }

        val tableConfigs = sessionTableConfigRepository.findBySessionId(sessionId)
        val tableConfigMap = tableConfigs.associateBy { it.tableId }

        // Build zone hierarchy map for breadcrumb display
        val zoneHierarchyMap = buildZoneHierarchyMap(chartStructure.zones)

        // Process seats
        val seats = chartStructure.seats.mapNotNull { seatDto ->
            val config = seatConfigMap[seatDto.id]
            if (config == null) {
                logger.warn { "Seat config missing for seat ${seatDto.code}" }
                return@mapNotNull null
            }

            SessionSeatResponse(
                seatIdentifier = seatDto.code,
                seatNumber = seatDto.seatNumber,
                rowLabel = seatDto.rowLabel,
                levels = zoneHierarchyMap[seatDto.zoneId] ?: listOf("Unknown"),
                positionX = seatDto.x,
                positionY = seatDto.y,
                status = config.status.name,
                price = config.priceTemplate?.price?.toString(),
                priceTemplateName = config.priceTemplate?.templateName,
                priceTemplateColor = config.priceTemplate?.color
            )
        }

        // Process GA areas
        val gaAreas = chartStructure.gaAreas.mapNotNull { gaDto ->
            val config = gaConfigMap[gaDto.id]
            if (config == null) {
                logger.warn { "GA config missing for GA area ${gaDto.code}" }
                return@mapNotNull null
            }

            val capacity = config.capacity ?: 0
            val available = config.getAvailableCapacity() ?: 0

            SessionGAAreaResponse(
                levelIdentifier = gaDto.code,
                levelName = gaDto.name,
                levels = zoneHierarchyMap[gaDto.zoneId] ?: listOf(gaDto.name),
                capacity = capacity,
                available = available,
                price = config.priceTemplate?.price?.toString(),
                priceTemplateName = config.priceTemplate?.templateName
            )
        }

        // Process tables
        val tables = chartStructure.tables.mapNotNull { tableDto ->
            val config = tableConfigMap[tableDto.id]
            if (config == null) {
                logger.warn { "Table config missing for table ${tableDto.code}" }
                return@mapNotNull null
            }

            // Find all seats for this table
            val tableSeats = chartStructure.seats.filter { it.tableId == tableDto.id }

            SessionTableResponse(
                tableId = tableDto.id,
                tableName = tableDto.tableNumber,
                tableIdentifier = tableDto.code,
                levels = zoneHierarchyMap[tableDto.zoneId] ?: listOf(tableDto.tableNumber),
                positionX = tableDto.x,
                positionY = tableDto.y,
                bookingMode = config.bookingMode.name,
                seatCount = tableSeats.size,
                seatIdentifiers = tableSeats.map { it.code },
                status = config.status.name,
                price = config.priceTemplate?.price?.toString(),
                priceTemplateName = config.priceTemplate?.templateName,
                priceTemplateColor = config.priceTemplate?.color
            )
        }

        // Calculate statistics
        val totalSeats = seatConfigs.size
        val availableSeats = seatConfigs.count { it.isAvailable() }
        val soldSeats = session.ticketsSold

        // Get price templates
        val priceTemplates = getPriceTemplatesForSession(event)

        logger.info {
            "Session seating loaded: $totalSeats seats ($availableSeats available), " +
                    "${tables.size} tables, ${gaAreas.size} GA areas"
        }

        return SessionSeatingResponse(
            sessionId = sessionId,
            eventId = event.id ?: throw VenuesException.ValidationFailure("Event ID is null"),
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
     * Build zone hierarchy map for breadcrumb display.
     * Returns Map<zoneId, List<zoneNames>> from root to leaf.
     * Example: zoneId=5 -> ["Orchestra", "Center", "Row A"]
     */
    private fun buildZoneHierarchyMap(zones: List<ZoneDto>): Map<Long, List<String>> {
        if (zones.isEmpty()) {
            return emptyMap()
        }

        val zoneMap = zones.associateBy { it.id }
        val hierarchyCache = mutableMapOf<Long, List<String>>()

        fun getHierarchy(zoneId: Long, visited: Set<Long> = emptySet()): List<String> {
            hierarchyCache[zoneId]?.let { return it }

            val zone = zoneMap[zoneId] ?: return emptyList()

            if (zoneId in visited) {
                logger.warn { "Circular zone hierarchy detected for zone $zoneId" }
                return listOf(zone.name)
            }

            val hierarchy = if (zone.parentZoneId == null) {
                listOf(zone.name)
            } else {
                val parentId = zone.parentZoneId
                if (parentId != null) {
                    val parentHierarchy = getHierarchy(parentId, visited + zoneId)
                    if (parentHierarchy.isNotEmpty()) {
                        parentHierarchy + zone.name
                    } else {
                        listOf(zone.name)
                    }
                } else {
                    listOf(zone.name)
                }
            }

            hierarchyCache[zoneId] = hierarchy
            return hierarchy
        }

        zones.forEach { zone ->
            if (zone.id !in hierarchyCache) {
                getHierarchy(zone.id)
            }
        }

        return hierarchyCache.toMap()
    }

    /**
     * Get quick availability info without full seating structure.
     * Optimized for large venues.
     */
    @Transactional(readOnly = true)
    fun getSessionAvailability(sessionId: UUID): SeatAvailabilityResponse {
        logger.debug { "Fetching seat availability for session: $sessionId" }

        val session = getSessionOrThrow(sessionId)

        val seatConfigs = sessionSeatConfigRepository.findBySessionId(sessionId)
        val totalSeats = seatConfigs.size
        val availableSeats = seatConfigs.count { it.isAvailable() }
        val reservedSeats = seatConfigs.count { it.status.name == "RESERVED" }

        // Only include seat codes for small venues (< 1000 seats)
        val availableIdentifiers = if (totalSeats < 1000) {
            seatConfigs
                .filter { it.isAvailable() }
                .mapNotNull { config ->
                    seatingApi.getSeatInfo(config.seatId)?.code
                }
                .sorted()
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
    private fun getPriceTemplatesForSession(event: Event): List<SessionPriceTemplateResponse> {
        val templates = mutableListOf<SessionPriceTemplateResponse>()

        // Add event-level price templates
        event.priceTemplates.forEach { template ->
            templates.add(
                SessionPriceTemplateResponse(
                    id = template.id,
                    templateName = template.templateName,
                    color = template.color,
                    price = template.price.toString(),
                    isOverride = false
                )
            )
        }

        return templates
    }

    /**
     * Get session or throw exception.
     */
    private fun getSessionOrThrow(sessionId: UUID): EventSession {
        return eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Event session not found") }
    }
}

