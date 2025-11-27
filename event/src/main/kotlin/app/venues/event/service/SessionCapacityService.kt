package app.venues.event.service

import app.venues.common.exception.VenuesException
import app.venues.event.domain.EventSession
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionGAConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Computes and persists aggregate ticket capacity for sessions.
 *
 * Seats are counted directly from the seating chart (optimized COUNT query)
 * while GA capacity is derived from session-level GA configs.
 * Tables are ignored because their seats are already included in the seat count.
 */
@Service
@Transactional
class SessionCapacityService(
    private val seatingApi: SeatingApi,
    private val gaConfigRepository: SessionGAConfigRepository,
    private val eventSessionRepository: EventSessionRepository
) {
    private val logger = KotlinLogging.logger {}

    fun recalculateForSession(sessionId: UUID) {
        val session = eventSessionRepository.findById(sessionId)
            .orElseThrow { VenuesException.ResourceNotFound("Event session not found: $sessionId") }
        recalculateForSession(session)
    }

    fun recalculateForSession(session: EventSession) {
        val chartId = session.event.seatingChartId
        if (chartId == null) {
            logger.warn { "Skipping capacity calculation for session ${session.id} because event ${session.event.id} has no seating chart" }
            session.ticketsCount = null
            return
        }

        // Use optimized COUNT query instead of fetching full structure
        val seatCount = seatingApi.getSeatCount(chartId).toLong()
        val gaCapacity = gaConfigRepository.sumCapacityBySessionId(session.id)
        val totalCapacity = seatCount + gaCapacity

        if (totalCapacity > Int.MAX_VALUE) {
            throw VenuesException.ValidationFailure("Total capacity $totalCapacity exceeds supported range for session ${session.id}")
        }

        session.ticketsCount = totalCapacity.toInt()
        logger.debug { "Updated ticketsCount for session ${session.id}: seats=$seatCount, ga=$gaCapacity, total=$totalCapacity" }
    }
}
