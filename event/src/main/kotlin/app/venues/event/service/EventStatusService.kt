package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.domain.Event
import app.venues.event.domain.EventSession
import app.venues.event.domain.EventStatus
import app.venues.event.domain.SessionStatus
import app.venues.event.repository.EventRepository
import app.venues.event.repository.EventSessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for managing event and session status transitions.
 *
 * Responsibilities:
 * - Validate and execute status transitions for Events
 * - Validate and execute status transitions for Sessions
 * - Provide information about allowed transitions
 * - Enforce business rules for status changes
 *
 * Status Transition Rules:
 *
 * Event Status Flow:
 * - DRAFT → PUBLISHED (publish event for public)
 * - DRAFT → DELETED (discard draft)
 * - PUBLISHED → SUSPENDED (temporary removal)
 * - PUBLISHED → ARCHIVED (event finished)
 * - PUBLISHED → DELETED (permanent removal with sales)
 * - SUSPENDED → PUBLISHED (resume event)
 * - SUSPENDED → DELETED (permanent removal)
 * - ARCHIVED → DELETED (cleanup old events)
 *
 * Session Status Flow:
 * - ON_SALE → PAUSED (temporary halt)
 * - ON_SALE → SOLD_OUT (manual sold out)
 * - ON_SALE → SALES_CLOSED (session started)
 * - ON_SALE → CANCELLED (cancel session)
 * - PAUSED → ON_SALE (resume sales)
 * - PAUSED → SOLD_OUT (mark sold out)
 * - PAUSED → SALES_CLOSED (close sales)
 * - PAUSED → CANCELLED (cancel session)
 * - SOLD_OUT → SALES_CLOSED (session started)
 * - SOLD_OUT → CANCELLED (cancel session)
 * - SALES_CLOSED → CANCELLED (cancel finished session)
 */
@Service
@Transactional
class EventStatusService(
    private val eventRepository: EventRepository,
    private val eventSessionRepository: EventSessionRepository
) {
    private val logger = KotlinLogging.logger {}

    // ===========================================
    // EVENT STATUS MANAGEMENT
    // ===========================================

    /**
     * Changes the status of an event with validation.
     *
     * @param eventId The ID of the event to update.
     * @param venueId The ID of the venue (for ownership check).
     * @param targetStatus The desired target status.
     * @param reason Optional reason for the status change (for logging/audit).
     * @return The updated Event entity.
     * @throws VenuesException.ResourceNotFound If event is not found.
     * @throws VenuesException.AuthorizationFailure If venueId does not match event owner.
     * @throws VenuesException.ValidationFailure If transition is not allowed.
     */
    fun changeEventStatus(
        eventId: UUID,
        venueId: UUID,
        targetStatus: EventStatus,
        reason: String?
    ): Event {
        logger.debug { "Changing event status: eventId=$eventId, targetStatus=$targetStatus, reason=$reason" }

        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        // Verify ownership
        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Event does not belong to this venue")
        }

        // Check if transition is allowed
        if (!event.canTransitionTo(targetStatus)) {
            throw VenuesException.ValidationFailure(
                "Cannot transition event from ${event.status} to $targetStatus"
            )
        }

        // Execute transition
        try {
            event.transitionTo(targetStatus)
        } catch (e: IllegalStateException) {
            throw VenuesException.ValidationFailure(e.message ?: "Invalid status transition")
        } catch (e: IllegalArgumentException) {
            throw VenuesException.ValidationFailure(e.message ?: "Invalid status transition")
        }

        val savedEvent = eventRepository.save(event)

        logger.info {
            "Event status changed successfully: eventId=$eventId, " +
                    "from=${event.status} to=$targetStatus, reason=${reason ?: "none"}"
        }

        return savedEvent
    }

    /**
     * Gets the allowed status transitions for an event.
     *
     * @param eventId The ID of the event.
     * @param venueId The ID of the venue (for ownership check).
     * @return Set of allowed target statuses.
     * @throws VenuesException.ResourceNotFound If event is not found.
     * @throws VenuesException.AuthorizationFailure If venueId does not match event owner.
     */
    @Transactional(readOnly = true)
    fun getAllowedEventTransitions(eventId: UUID, venueId: UUID): Set<EventStatus> {
        val event = eventRepository.findById(eventId)
            .orElseThrow { VenuesException.ResourceNotFound("Event not found with ID: $eventId") }

        if (event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Event does not belong to this venue")
        }

        return EventStatus.entries.filter { event.canTransitionTo(it) }.toSet()
    }

    // ===========================================
    // SESSION STATUS MANAGEMENT
    // ===========================================

    /**
     * Changes the status of a session with validation.
     *
     * @param sessionId The ID of the session to update.
     * @param venueId The ID of the venue (for ownership check via event).
     * @param targetStatus The desired target status.
     * @param reason Optional reason for the status change (for logging/audit).
     * @return The updated EventSession entity.
     * @throws VenuesException.ResourceNotFound If session is not found.
     * @throws VenuesException.AuthorizationFailure If venueId does not match event owner.
     * @throws VenuesException.ValidationFailure If transition is not allowed.
     */
    fun changeSessionStatus(
        sessionId: UUID,
        venueId: UUID,
        targetStatus: SessionStatus,
        reason: String?
    ): EventSession {
        logger.debug { "Changing session status: sessionId=$sessionId, targetStatus=$targetStatus, reason=$reason" }

        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found with ID: $sessionId") }

        // Verify ownership via event
        if (session.event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Session does not belong to this venue")
        }

        // Check if transition is allowed
        if (!session.canTransitionTo(targetStatus)) {
            throw VenuesException.ValidationFailure(
                "Cannot transition session from ${session.status} to $targetStatus"
            )
        }

        // Execute transition
        try {
            session.transitionTo(targetStatus)
        } catch (e: IllegalStateException) {
            throw VenuesException.ValidationFailure(e.message ?: "Invalid status transition")
        } catch (e: IllegalArgumentException) {
            throw VenuesException.ValidationFailure(e.message ?: "Invalid status transition")
        }

        val savedSession = eventSessionRepository.save(session)

        logger.info {
            "Session status changed successfully: sessionId=$sessionId, " +
                    "from=${session.status} to=$targetStatus, reason=${reason ?: "none"}"
        }

        // TODO: If status is CANCELLED, trigger refund workflows via event publication

        return savedSession
    }

    /**
     * Gets the allowed status transitions for a session.
     *
     * @param sessionId The ID of the session.
     * @param venueId The ID of the venue (for ownership check).
     * @return Set of allowed target statuses.
     * @throws VenuesException.ResourceNotFound If session is not found.
     * @throws VenuesException.AuthorizationFailure If venueId does not match event owner.
     */
    @Transactional(readOnly = true)
    fun getAllowedSessionTransitions(sessionId: UUID, venueId: UUID): Set<SessionStatus> {
        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Session not found with ID: $sessionId") }

        if (session.event.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Session does not belong to this venue")
        }

        return SessionStatus.entries.filter { session.canTransitionTo(it) }.toSet()
    }

    // ===========================================
    // BATCH OPERATIONS
    // ===========================================

    /**
     * Changes status for multiple sessions at once.
     * Useful for bulk operations like pausing all sessions of an event.
     *
     * @param sessionIds List of session IDs to update.
     * @param venueId The ID of the venue (for ownership check).
     * @param targetStatus The desired target status.
     * @param reason Optional reason for the status change.
     * @return Map of sessionId to success/failure result.
     */
    fun bulkChangeSessionStatus(
        sessionIds: List<UUID>,
        venueId: UUID,
        targetStatus: SessionStatus,
        reason: String?
    ): Map<UUID, Result<EventSession>> {
        logger.debug { "Bulk changing session status: count=${sessionIds.size}, targetStatus=$targetStatus" }

        val results = mutableMapOf<UUID, Result<EventSession>>()

        sessionIds.forEach { sessionId ->
            results[sessionId] = runCatching {
                changeSessionStatus(sessionId, venueId, targetStatus, reason)
            }
        }

        val successCount = results.values.count { it.isSuccess }
        logger.info { "Bulk session status change completed: total=${sessionIds.size}, success=$successCount" }

        return results
    }
}
