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
    private val gaConfigRepository: SessionGAConfigRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Generates the initial inventory configuration for a session based on the event's seating chart.
     * This should be called when a session is created or when a chart is first assigned.
     */
    fun generateConfigsForSession(
        session: EventSession,
        chartId: UUID,
        priceTemplates: List<EventPriceTemplate>
    ) {
        logger.debug { "Generating seating configs for session: ${session.id} using chart: $chartId" }

        val structure = seatingApi.getChartStructure(chartId)
            ?: throw VenuesException.ResourceNotFound("Seating chart not found: $chartId")

        // Map templates by name for quick lookup (matching category keys from chart)
        val templateMap = priceTemplates.associateBy { it.templateName }

        // 1. Generate Seat Configs
        val seatConfigs = structure.seats.map { seatDto ->
            SessionSeatConfig(
                session = session,
                seatId = seatDto.id,
                priceTemplate = templateMap[seatDto.categoryKey] // Auto-match if template name == category key
            )
        }
        if (seatConfigs.isNotEmpty()) {
            seatConfigRepository.saveAll(seatConfigs)
        }

        // 2. Generate Table Configs
        val tableConfigs = structure.tables.map { tableDto ->
            SessionTableConfig(
                session = session,
                tableId = tableDto.id,
                priceTemplate = null // Tables usually default to null until explicitly priced
            )
        }
        if (tableConfigs.isNotEmpty()) {
            tableConfigRepository.saveAll(tableConfigs)
        }

        // 3. Generate GA Configs
        val gaConfigs = structure.gaAreas.map { gaDto ->
            SessionGAConfig(
                session = session,
                gaAreaId = gaDto.id,
                capacity = gaDto.capacity,
                priceTemplate = null
            )
        }
        if (gaConfigs.isNotEmpty()) {
            gaConfigRepository.saveAll(gaConfigs)
        }

        logger.info { "Generated ${seatConfigs.size} seats, ${tableConfigs.size} tables, ${gaConfigs.size} GA areas for session ${session.id}" }
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
