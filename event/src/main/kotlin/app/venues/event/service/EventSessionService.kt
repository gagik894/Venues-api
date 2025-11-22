package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.api.dto.EventSessionRequest
import app.venues.event.api.mapper.EventMapper
import app.venues.event.domain.Event
import app.venues.event.domain.EventPriceTemplate
import app.venues.event.domain.EventSession
import app.venues.event.domain.EventSessionPriceOverride
import app.venues.event.repository.EventSessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for managing Event Sessions.
 *
 * Responsibilities:
 * - Creating, Updating, Deleting sessions.
 * - Managing session-specific price overrides.
 * - Coordinating with EventSeatingService to generate inventory.
 */
@Service
@Transactional
class EventSessionService(
    private val eventSessionRepository: EventSessionRepository,
    private val eventSeatingService: EventSeatingService,
    private val eventMapper: EventMapper
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Updates the sessions collection of an event based on the request.
     * Handles creation, update, and safe deletion.
     */
    fun updateSessions(event: Event, requests: List<EventSessionRequest>) {
        val existingSessionsMap = event.sessions.associateBy { it.id }
        val requestSessionIds = requests.mapNotNull { it.id }.toSet()

        // 1. Remove deleted sessions
        val sessionsToRemove = event.sessions.filter { it.id !in requestSessionIds }
        sessionsToRemove.forEach { session ->
            if (session.ticketsSold > 0) {
                throw VenuesException.ValidationFailure("Cannot remove session with sold tickets: ${session.id}")
            }
        }
        event.sessions.removeAll(sessionsToRemove)

        // 2. Update or Create
        requests.forEach { request ->
            if (request.id != null && existingSessionsMap.containsKey(request.id)) {
                // Update existing
                val session = existingSessionsMap[request.id]!!
                updateSessionEntity(session, request)
            } else {
                // Create new
                val session = createSessionEntity(event, request)
                event.sessions.add(session)

                // If event has a chart, generate configs immediately
                // Note: We need to save the session first or rely on cascade. 
                // Since we are adding to event.sessions and event will be saved, cascade should handle it.
                // BUT, generating configs requires session ID usually if we use repositories directly.
                // We might need to defer config generation until after save.
            }
        }
    }

    /**
     * Generates seating configs for all sessions in the event.
     * Should be called after the event (and sessions) are saved.
     */
    fun generateConfigsForNewSessions(event: Event) {
        if (event.seatingChartId == null) return

        // Ensure price templates exist for all categories in the chart (Requirement 1)
        eventSeatingService.ensurePriceTemplatesForChart(event, event.seatingChartId!!)

        event.sessions.forEach { session ->
            // Check if configs already exist using repositories to avoid loading large collections
            val hasSeats = eventSeatingService.hasConfigs(session.id)

            if (!hasSeats) {
                eventSeatingService.generateConfigsForSession(session, event.seatingChartId!!, event.priceTemplates)
            }
        }
    }

    private fun createSessionEntity(event: Event, request: EventSessionRequest): EventSession {
        validateTime(request)

        val session = EventSession(
            event = event,
            startTime = request.startTime,
            endTime = request.endTime,
            ticketsCount = request.ticketsCount,
            priceOverride = request.priceOverride,
            priceRangeOverride = request.priceRangeOverride
        )

        updatePriceOverrides(session, request)
        return session
    }

    private fun updateSessionEntity(session: EventSession, request: EventSessionRequest) {
        validateTime(request)

        session.startTime = request.startTime
        session.endTime = request.endTime
        session.ticketsCount = request.ticketsCount
        session.priceOverride = request.priceOverride
        session.priceRangeOverride = request.priceRangeOverride

        updatePriceOverrides(session, request)
    }

    private fun updatePriceOverrides(session: EventSession, request: EventSessionRequest) {
        session.priceTemplateOverrides.clear()
        request.priceTemplateOverrides.forEach { overrideRequest ->
            val override = EventSessionPriceOverride(
                session = session,
                templateName = overrideRequest.templateName,
                price = overrideRequest.price
            )
            session.priceTemplateOverrides.add(override)
        }
    }

    private fun validateTime(request: EventSessionRequest) {
        if (request.startTime.isAfter(request.endTime)) {
            throw VenuesException.ValidationFailure("Start time must be before end time")
        }
    }

    /**
     * Batch assigns a price template to a list of seats.
     */
    fun assignPriceTemplateToSeats(sessionId: UUID, template: EventPriceTemplate?, seatIds: List<Long>) {
        eventSeatingService.assignPriceTemplateToSeats(sessionId, template, seatIds)
    }

    /**
     * Batch assigns a price template to a list of tables.
     */
    fun assignPriceTemplateToTables(sessionId: UUID, template: EventPriceTemplate?, tableIds: List<Long>) {
        eventSeatingService.assignPriceTemplateToTables(sessionId, template, tableIds)
    }

    /**
     * Assigns a price template to a GA area.
     */
    fun assignPriceTemplateToGa(sessionId: UUID, template: EventPriceTemplate?, gaId: Long) {
        eventSeatingService.assignPriceTemplateToGa(sessionId, template, gaId)
    }
}
