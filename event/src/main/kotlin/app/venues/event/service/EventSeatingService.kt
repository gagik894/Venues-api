package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.domain.*
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.event.repository.SessionTableConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service responsible for managing the seating configuration (inventory) of event sessions.
 *
 * Responsibilities:
 * - Generating initial seat/table/GA configs from a Seating Chart.
 * - Updating configs when the chart changes (if allowed).
 * - Batch assigning price templates to seats/tables.
 * - Managing availability states (blocking/unblocking).
 */
@Service
@Transactional
class EventSeatingService(
    private val seatingApi: SeatingApi,
    private val seatConfigRepository: SessionSeatConfigRepository,
    private val tableConfigRepository: SessionTableConfigRepository,
    private val gaConfigRepository: SessionGAConfigRepository,
    private val sessionCapacityService: SessionCapacityService,
    private val seatConfigSparseService: SeatConfigSparseService
) {
    private val logger = KotlinLogging.logger {}

    private val colorPalette = listOf(
        "#1F77B4", // Muted Blue
        "#FF7F0E", // Safety Orange
        "#2CA02C", // Cooked Asparagus Green
        "#D62728", // Brick Red
        "#9467BD", // Muted Purple
        "#8C564B", // Chestnut Brown
        "#E377C2", // Raspberry Yogurt Pink
        "#7F7F7F", // Middle Gray
        "#BCBD22", // Curry Yellow-Green
        "#17BECF"  // Blue-Teal
    )

    /**
     * Ensures that the event has price templates for all category keys in the chart.
     * This implements the "Auto-Fill & Coloring" requirement.
     */
    fun ensurePriceTemplatesForChart(event: Event, chartId: UUID) {
        val structure = seatingApi.getChartStructure(chartId)
            ?: throw VenuesException.ResourceNotFound("Seating chart not found: $chartId")

        val existingTemplateNames = event.priceTemplates.map { it.templateName }.toSet()
        val distinctCategories = buildList {
            addAll(structure.seats.map { it.categoryKey })
            addAll(structure.tables.map { it.categoryKey })
            addAll(structure.gaAreas.map { it.categoryKey })
        }
            .filter { it.isNotBlank() }
            .distinct()

        var newTemplatesCount = 0
        distinctCategories.forEachIndexed { index, categoryKey ->
            if (categoryKey !in existingTemplateNames) {
                val color = colorPalette[index % colorPalette.size]
                val newTemplate = EventPriceTemplate(
                    event = event,
                    templateName = categoryKey,
                    price = java.math.BigDecimal.ZERO, // Default price, must be set by admin later
                    color = color,
                    isAnchor = true
                )
                event.priceTemplates.add(newTemplate)
                newTemplatesCount++
            }
        }

        if (newTemplatesCount > 0) {
            logger.info { "Generated $newTemplatesCount new price templates for event ${event.id}" }
        }
    }

    /**
     * Generates the initial inventory configuration for a session based on the event's seating chart.
     * This should be called when a session is created or when a chart is first assigned.
     *
     * REFACTOR: Now follows "Sparse Matrix" pattern.
     * - Seats: Lazy loaded. No rows created initially.
     * - Tables/GA: Created as before (low volume).
     */
    fun generateConfigsForSession(
        session: EventSession,
        chartId: UUID,
        priceTemplates: Collection<EventPriceTemplate>
    ) {
        logger.debug { "Generating seating configs for session: ${session.id} using chart: $chartId" }

        val structure = seatingApi.getChartStructure(chartId)
            ?: throw VenuesException.ResourceNotFound("Seating chart not found: $chartId")

        val templatesByName = priceTemplates.associateBy { it.templateName }

        // 1. Generate Seat Configs - SKIPPED (Sparse Matrix Pattern)
        // We do NOT create rows for seats. They default to AVAILABLE and use the EventPriceTemplate
        // matching their categoryKey. Rows are only created on state change (Reserve/Block).
        logger.info { "Skipping initial seat config generation for session ${session.id} (Sparse Matrix Pattern)" }

        // 2. Generate Table Configs
        val tableConfigs = structure.tables.map { tableDto ->
            val defaultTemplate = templatesByName[tableDto.categoryKey]
            if (defaultTemplate == null) {
                logger.warn { "No price template found for table category ${tableDto.categoryKey} in event ${session.event.id}" }
            }
            SessionTableConfig(
                session = session,
                tableId = tableDto.id,
                priceTemplate = defaultTemplate // Default to chart category template (if available)
            )
        }
        if (tableConfigs.isNotEmpty()) {
            tableConfigRepository.saveAll(tableConfigs)
        }

        // 3. Generate GA Configs
        val gaConfigs = structure.gaAreas.map { gaDto ->
            val defaultTemplate = templatesByName[gaDto.categoryKey]
            if (defaultTemplate == null) {
                logger.warn { "No price template found for GA category ${gaDto.categoryKey} in event ${session.event.id}" }
            }
            SessionGAConfig(
                session = session,
                gaAreaId = gaDto.id,
                capacity = gaDto.capacity,
                priceTemplate = defaultTemplate
            )
        }
        if (gaConfigs.isNotEmpty()) {
            gaConfigRepository.saveAll(gaConfigs)
        }

        logger.info { "Generated ${tableConfigs.size} tables, ${gaConfigs.size} GA areas for session ${session.id}" }

        sessionCapacityService.recalculateForSession(session)
    }

    /**
     * Batch assigns a price template to a list of seats.
     * This is the "Select seats -> Apply Price" feature.
     */
    fun assignPriceTemplateToSeats(
        sessionId: UUID,
        template: EventPriceTemplate?,
        seatIds: List<Long>
    ) {
        logger.debug { "Assigning template ${template?.templateName} to ${seatIds.size} seats in session $sessionId" }

        if (seatIds.isEmpty()) return

        // Optimized batch update for performance (handles 10k+ seats efficiently)
        val updatedCount = seatConfigRepository.batchUpdatePriceTemplate(sessionId, seatIds, template)
        logger.info { "Updated price template for $updatedCount seats in session $sessionId" }

        // Remove redundant rows when seats fall back to the default price while available
        seatConfigSparseService.purgeDefaultRows(sessionId, seatIds)
    }

    /**
     * Batch assigns a price template to a list of tables.
     */
    fun assignPriceTemplateToTables(
        sessionId: UUID,
        template: EventPriceTemplate?,
        tableIds: List<Long>
    ) {
        logger.debug { "Assigning template ${template?.templateName} to ${tableIds.size} tables in session $sessionId" }

        if (tableIds.isEmpty()) return

        val updatedCount = tableConfigRepository.batchUpdatePriceTemplate(sessionId, tableIds, template)
        logger.info { "Updated price template for $updatedCount tables in session $sessionId" }
    }

    /**
     * Assigns a price template to a GA area.
     */
    fun assignPriceTemplateToGa(
        sessionId: UUID,
        template: EventPriceTemplate?,
        gaId: Long
    ) {
        val config = gaConfigRepository.findBySessionIdAndGaAreaId(sessionId, gaId)
            ?: throw VenuesException.ResourceNotFound("GA config not found for session $sessionId and GA area $gaId")

        config.priceTemplate = template
        gaConfigRepository.save(config)
    }

    /**
     * Checks if any seating configuration exists for the session.
     */
    fun hasConfigs(sessionId: UUID): Boolean {
        return seatConfigRepository.existsBySessionId(sessionId) ||
                tableConfigRepository.existsBySessionId(sessionId) ||
                gaConfigRepository.existsBySessionId(sessionId)
    }
}
