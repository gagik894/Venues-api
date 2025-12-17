package app.venues.event.service

import app.venues.event.domain.ConfigStatus
import app.venues.event.repository.EventSessionRepository
import app.venues.event.repository.SessionSeatConfigRepository
import app.venues.seating.api.SeatingApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Ensures the "sparse matrix" contract for session seat configs by removing
 * rows that represent default, available seats. This avoids storing millions
 * of redundant configs when hosts rely on chart defaults.
 */
@Service
@Transactional
class SeatConfigSparseService(
    private val eventSessionRepository: EventSessionRepository,
    private val sessionSeatConfigRepository: SessionSeatConfigRepository,
    private val seatingApi: SeatingApi
) {
    private val logger = KotlinLogging.logger {}

    fun purgeDefaultRows(sessionId: UUID, seatIds: Collection<Long>) {
        val distinctSeatIds = seatIds.distinct()
        if (distinctSeatIds.isEmpty()) {
            return
        }

        val configs = sessionSeatConfigRepository.findBySessionIdAndSeatIdIn(sessionId, distinctSeatIds)
        if (configs.isEmpty()) {
            return
        }

        val session = configs.firstOrNull()?.session
            ?: eventSessionRepository.findById(sessionId).orElse(null)
            ?: return
        val defaultTemplatesByCategory = session.event.priceTemplates.associateBy { it.templateName }

        val seatInfoMap = seatingApi.getSeatInfoBatch(configs.map { it.seatId }).associateBy { it.id }

        val deletable = configs.filter { config ->
            if (config.status != ConfigStatus.AVAILABLE) {
                return@filter false
            }
            val seatInfo = seatInfoMap[config.seatId] ?: return@filter false
            val defaultTemplate = defaultTemplatesByCategory[seatInfo.categoryKey] ?: return@filter false
            val configTemplateId = config.priceTemplate?.id ?: return@filter false
            configTemplateId == defaultTemplate.id
        }

        if (deletable.isEmpty()) {
            return
        }

        sessionSeatConfigRepository.deleteAllInBatch(deletable)
        logger.debug { "Removed ${deletable.size} redundant seat configs for session $sessionId" }
    }
}
