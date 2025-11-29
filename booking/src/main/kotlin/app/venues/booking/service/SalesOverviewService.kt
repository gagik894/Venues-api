package app.venues.booking.service

import app.venues.booking.api.dto.EventSalesOverview
import app.venues.booking.api.dto.SessionSalesOverview
import app.venues.booking.repository.BookingRepository
import app.venues.common.exception.VenuesException
import app.venues.event.api.EventApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

/**
 * Service for sales overview statistics.
 *
 * Aggregates ticket counts from EventApi and revenue from BookingRepository.
 */
@Service
class SalesOverviewService(
    private val bookingRepository: BookingRepository,
    private val eventApi: EventApi
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get sales overview for a single session.
     */
    @Transactional(readOnly = true)
    fun getSessionSalesOverview(sessionId: UUID, venueId: UUID): SessionSalesOverview {
        logger.debug { "Getting sales overview for session $sessionId" }

        // Get ticket stats from event module
        val ticketStats = eventApi.getSessionTicketStats(sessionId)
            ?: throw VenuesException.ResourceNotFound("Session not found: $sessionId")

        // Validate venue ownership via session's event venue
        val sessionInfo = eventApi.getEventSessionInfo(sessionId)
        if (sessionInfo?.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Session does not belong to this venue")
        }

        // Get revenue and booking count from booking module
        val totalRevenue = bookingRepository.sumRevenueBySessionId(sessionId)
        val confirmedBookingsCount = bookingRepository.countConfirmedBySessionId(sessionId)

        return SessionSalesOverview(
            sessionId = ticketStats.sessionId,
            eventId = ticketStats.eventId,
            ticketsSold = ticketStats.ticketsSold,
            ticketsTotal = ticketStats.ticketsTotal,
            totalRevenue = totalRevenue,
            confirmedBookingsCount = confirmedBookingsCount,
            currency = ticketStats.currency
        )
    }

    /**
     * Get sales overview for an entire event (aggregated across all sessions).
     */
    @Transactional(readOnly = true)
    fun getEventSalesOverview(eventId: UUID, venueId: UUID): EventSalesOverview {
        logger.debug { "Getting sales overview for event $eventId" }

        // Get ticket stats for all sessions from event module
        val sessionStats = eventApi.getEventTicketStats(eventId)
        if (sessionStats.isEmpty()) {
            throw VenuesException.ResourceNotFound("Event not found or has no sessions: $eventId")
        }

        // Validate venue ownership via first session
        val firstSessionId = sessionStats.first().sessionId
        val sessionInfo = eventApi.getEventSessionInfo(firstSessionId)
        if (sessionInfo?.venueId != venueId) {
            throw VenuesException.AuthorizationFailure("Event does not belong to this venue")
        }

        val sessionIds = sessionStats.map { it.sessionId }
        val currency = sessionStats.first().currency

        // Get aggregated revenue and counts
        val revenueBySession = getRevenueBySession(sessionIds)
        val countBySession = getBookingCountBySession(sessionIds)

        // Build per-session overviews
        val sessionOverviews = sessionStats.map { stats ->
            SessionSalesOverview(
                sessionId = stats.sessionId,
                eventId = stats.eventId,
                ticketsSold = stats.ticketsSold,
                ticketsTotal = stats.ticketsTotal,
                totalRevenue = revenueBySession[stats.sessionId] ?: BigDecimal.ZERO,
                confirmedBookingsCount = countBySession[stats.sessionId] ?: 0,
                currency = stats.currency
            )
        }

        // Aggregate totals
        val totalTicketsSold = sessionStats.sumOf { it.ticketsSold }
        val totalTicketsTotal = if (sessionStats.all { it.ticketsTotal != null }) {
            sessionStats.sumOf { it.ticketsTotal!! }
        } else {
            null // If any session has unlimited tickets, total is unknown
        }
        val totalRevenue = sessionOverviews.sumOf { it.totalRevenue }
        val totalConfirmedBookings = sessionOverviews.sumOf { it.confirmedBookingsCount }

        return EventSalesOverview(
            eventId = eventId,
            ticketsSold = totalTicketsSold,
            ticketsTotal = totalTicketsTotal,
            totalRevenue = totalRevenue,
            confirmedBookingsCount = totalConfirmedBookings,
            currency = currency,
            sessions = sessionOverviews
        )
    }

    private fun getRevenueBySession(sessionIds: List<UUID>): Map<UUID, BigDecimal> {
        if (sessionIds.isEmpty()) return emptyMap()

        return bookingRepository.sumRevenueGroupedBySessionIds(sessionIds)
            .associate { row ->
                val sessionId = row[0] as UUID
                val revenue = row[1] as BigDecimal
                sessionId to revenue
            }
    }

    private fun getBookingCountBySession(sessionIds: List<UUID>): Map<UUID, Int> {
        if (sessionIds.isEmpty()) return emptyMap()

        return bookingRepository.countConfirmedGroupedBySessionIds(sessionIds)
            .associate { row ->
                val sessionId = row[0] as UUID
                val count = (row[1] as Long).toInt()
                sessionId to count
            }
    }
}
