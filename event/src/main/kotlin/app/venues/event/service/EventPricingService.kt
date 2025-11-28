package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.*
import app.venues.event.domain.EventPriceTemplate
import app.venues.event.repository.EventRepository
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for event-level pricing configuration.
 *
 * Aggregates pricing from all sessions to provide a unified view:
 * - If all sessions have same template -> show that template
 * - If sessions differ -> mark as "mixed"
 *
 * Does NOT include availability info (sold/reserved status).
 */
@Service
@Transactional
class EventPricingService(
    private val eventRepository: EventRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val sessionTableConfigRepository: SessionTableConfigRepository,
    private val sessionGAConfigRepository: SessionGAConfigRepository,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get aggregated pricing configuration across all sessions.
     *
     * Logic:
     * - If all sessions have the same template for a seat -> return that priceTemplateId
     * - If sessions differ -> return priceTemplateId: null, isMixed: true
     */
    @Transactional(readOnly = true)
    fun getPricingConfiguration(eventId: UUID, venueId: UUID): EventPricingConfigurationResponse {
        logger.debug { "Fetching pricing configuration for event: $eventId" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Not authorized to access this event")
        }

        val chartId = event.seatingChartId
        val sessionIds = event.sessions.map { it.id }

        // Build price templates list
        val priceTemplates = event.priceTemplates.map { template ->
            PriceTemplateResponse(
                id = template.id,
                templateName = template.templateName,
                price = template.price.toPlainString(),
                color = template.color,
                isRemovable = !template.isAnchor
            )
        }
        val templatesById = event.priceTemplates.associateBy { it.id }

        // If no sessions, return empty config based on chart structure
        if (sessionIds.isEmpty()) {
            return buildEmptyConfigFromChart(eventId, chartId, priceTemplates, event.priceTemplates)
        }

        // Aggregate seat pricing across sessions
        val seatMap = aggregateSeatPricing(sessionIds, templatesById)

        // Aggregate table pricing across sessions
        val tableMap = aggregateTablePricing(sessionIds, templatesById)

        // Aggregate GA pricing across sessions
        val gaMap = aggregateGAPricing(sessionIds, templatesById)

        return EventPricingConfigurationResponse(
            eventId = eventId,
            seatingChartId = chartId,
            priceTemplates = priceTemplates,
            seats = seatMap,
            tables = tableMap,
            gaAreas = gaMap
        )
    }

    /**
     * Assign price template to all sessions of the event.
     */
    fun assignEventPricing(
        eventId: UUID,
        venueId: UUID,
        request: EventPricingAssignRequest
    ) {
        logger.debug { "Assigning event-level pricing for event: $eventId, template: ${request.templateId}" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Not authorized to modify this event")
        }

        val template = event.priceTemplates.find { it.id == request.templateId }
            ?: throw VenuesException.ResourceNotFound("Price template not found: ${request.templateId}")

        val seatIds = request.seatIds ?: emptyList()
        val tableIds = request.tableIds ?: emptyList()
        val gaIds = request.gaIds ?: emptyList()

        val sessionIds = event.sessions.map { it.id }

        if (sessionIds.isEmpty()) {
            logger.warn { "No sessions to update for event $eventId" }
            return
        }

        // Apply to all sessions (skipping SOLD/RESERVED items)
        sessionIds.forEach { sessionId ->
            if (seatIds.isNotEmpty()) {
                sessionSeatConfigRepository.batchUpdatePriceTemplate(sessionId, seatIds, template)
            }
            if (tableIds.isNotEmpty()) {
                sessionTableConfigRepository.batchUpdatePriceTemplate(sessionId, tableIds, template)
            }
            if (gaIds.isNotEmpty()) {
                gaIds.forEach { gaId ->
                    val config = sessionGAConfigRepository.findBySessionIdAndGaAreaId(sessionId, gaId)
                    // Only update AVAILABLE GA areas (not BLOCKED)
                    if (config != null && config.isAvailable()) {
                        config.priceTemplate = template
                        sessionGAConfigRepository.save(config)
                    }
                }
            }
        }

        logger.info { "Applied pricing to ${sessionIds.size} sessions: ${seatIds.size} seats, ${tableIds.size} tables, ${gaIds.size} GA areas" }
    }

    // =================================================================================
    // PRIVATE HELPERS
    // =================================================================================

    /**
     * Aggregate seat pricing across all sessions.
     * If all sessions agree on template -> return it. If they differ -> isMixed = true.
     */
    private fun aggregateSeatPricing(
        sessionIds: List<UUID>,
        templatesById: Map<UUID, EventPriceTemplate>
    ): Map<Long, SeatPricingDto> {
        // seatId -> list of templateIds from each session (null if no template)
        val seatTemplateMap = mutableMapOf<Long, MutableSet<UUID?>>()

        sessionIds.forEach { sessionId ->
            val configs = sessionSeatConfigRepository.findBySessionId(sessionId)
            configs.forEach { config ->
                seatTemplateMap
                    .getOrPut(config.seatId) { mutableSetOf() }
                    .add(config.priceTemplate?.id)
            }
        }

        return seatTemplateMap.mapValues { (_, templateIds) ->
            if (templateIds.size == 1) {
                // All sessions agree
                val templateId = templateIds.first()
                val template = templateId?.let { templatesById[it] }
                SeatPricingDto(
                    priceTemplateId = templateId,
                    templateName = template?.templateName,
                    color = template?.color,
                    isMixed = false
                )
            } else {
                // Sessions differ
                SeatPricingDto(
                    priceTemplateId = null,
                    templateName = null,
                    color = null,
                    isMixed = true
                )
            }
        }
    }

    /**
     * Aggregate table pricing across all sessions.
     */
    private fun aggregateTablePricing(
        sessionIds: List<UUID>,
        templatesById: Map<UUID, EventPriceTemplate>
    ): Map<Long, TablePricingDto> {
        val tableTemplateMap = mutableMapOf<Long, MutableSet<UUID?>>()

        sessionIds.forEach { sessionId ->
            val configs = sessionTableConfigRepository.findBySessionId(sessionId)
            configs.forEach { config ->
                tableTemplateMap
                    .getOrPut(config.tableId) { mutableSetOf() }
                    .add(config.priceTemplate?.id)
            }
        }

        return tableTemplateMap.mapValues { (_, templateIds) ->
            if (templateIds.size == 1) {
                val templateId = templateIds.first()
                val template = templateId?.let { templatesById[it] }
                TablePricingDto(
                    priceTemplateId = templateId,
                    templateName = template?.templateName,
                    color = template?.color,
                    isMixed = false
                )
            } else {
                TablePricingDto(
                    priceTemplateId = null,
                    templateName = null,
                    color = null,
                    isMixed = true
                )
            }
        }
    }

    /**
     * Aggregate GA pricing across all sessions.
     */
    private fun aggregateGAPricing(
        sessionIds: List<UUID>,
        templatesById: Map<UUID, EventPriceTemplate>
    ): Map<Long, GAPricingDto> {
        val gaTemplateMap = mutableMapOf<Long, MutableSet<UUID?>>()

        sessionIds.forEach { sessionId ->
            val configs = sessionGAConfigRepository.findBySessionId(sessionId)
            configs.forEach { config ->
                gaTemplateMap
                    .getOrPut(config.gaAreaId) { mutableSetOf() }
                    .add(config.priceTemplate?.id)
            }
        }

        return gaTemplateMap.mapValues { (_, templateIds) ->
            if (templateIds.size == 1) {
                val templateId = templateIds.first()
                val template = templateId?.let { templatesById[it] }
                GAPricingDto(
                    priceTemplateId = templateId,
                    templateName = template?.templateName,
                    color = template?.color,
                    isMixed = false
                )
            } else {
                GAPricingDto(
                    priceTemplateId = null,
                    templateName = null,
                    color = null,
                    isMixed = true
                )
            }
        }
    }

    /**
     * Build empty config when no sessions exist yet, using chart structure.
     */
    private fun buildEmptyConfigFromChart(
        eventId: UUID,
        chartId: UUID?,
        priceTemplates: List<PriceTemplateResponse>,
        templates: Collection<EventPriceTemplate>
    ): EventPricingConfigurationResponse {
        if (chartId == null) {
            return EventPricingConfigurationResponse(
                eventId = eventId,
                seatingChartId = null,
                priceTemplates = priceTemplates,
                seats = emptyMap(),
                tables = emptyMap(),
                gaAreas = emptyMap()
            )
        }

        val structure = seatingApi.getChartStructure(chartId)
            ?: return EventPricingConfigurationResponse(
                eventId = eventId,
                seatingChartId = chartId,
                priceTemplates = priceTemplates,
                seats = emptyMap(),
                tables = emptyMap(),
                gaAreas = emptyMap()
            )

        val templatesByName = templates.associateBy { it.templateName }

        // Map seats based on chart category -> template
        val seatMap = structure.seats.associate { seat ->
            val template = templatesByName[seat.categoryKey]
            seat.id to SeatPricingDto(
                priceTemplateId = template?.id,
                templateName = template?.templateName,
                color = template?.color,
                isMixed = false
            )
        }

        val tableMap = structure.tables.associate { table ->
            val template = templatesByName[table.categoryKey]
            table.id to TablePricingDto(
                priceTemplateId = template?.id,
                templateName = template?.templateName,
                color = template?.color,
                isMixed = false
            )
        }

        val gaMap = structure.gaAreas.associate { ga ->
            val template = templatesByName[ga.categoryKey]
            ga.id to GAPricingDto(
                priceTemplateId = template?.id,
                templateName = template?.templateName,
                color = template?.color,
                isMixed = false
            )
        }

        return EventPricingConfigurationResponse(
            eventId = eventId,
            seatingChartId = chartId,
            priceTemplates = priceTemplates,
            seats = seatMap,
            tables = tableMap,
            gaAreas = gaMap
        )
    }
}
