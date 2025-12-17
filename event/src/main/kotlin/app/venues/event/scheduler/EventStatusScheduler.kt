package app.venues.event.scheduler

import app.venues.event.domain.EventStatus
import app.venues.event.domain.SessionStatus
import app.venues.event.repository.EventRepository
import app.venues.event.repository.EventSessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Scheduler for automatic status updates of Events and Sessions.
 */
@Component
class EventStatusScheduler(
    private val eventSessionRepository: EventSessionRepository,
    private val eventRepository: EventRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Runs every minute to close sessions that have started.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    fun closeExpiredSessions() {
        val now = Instant.now()
        val count = eventSessionRepository.updateStatusForExpiredSessions(
            oldStatus = SessionStatus.ON_SALE,
            newStatus = SessionStatus.SALES_CLOSED,
            now = now
        )
        if (count > 0) {
            logger.info { "Closed $count expired sessions at $now" }
        }
    }

    /**
     * Runs every hour to archive events that finished more than 24 hours ago.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    fun archivePastEvents() {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        val count = eventRepository.archivePastEvents(
            oldStatus = EventStatus.PUBLISHED,
            newStatus = EventStatus.ARCHIVED,
            cutoff = cutoff
        )
        if (count > 0) {
            logger.info { "Archived $count past events at $cutoff" }
        }
    }
}
