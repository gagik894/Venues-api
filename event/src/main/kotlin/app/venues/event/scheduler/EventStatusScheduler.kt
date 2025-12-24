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

    private lateinit var adaptiveTask: app.venues.shared.scheduling.AdaptiveScheduledTask

    @jakarta.annotation.PostConstruct
    fun init() {
        adaptiveTask = app.venues.shared.scheduling.AdaptiveScheduledTask(
            taskName = "EventSessionClose",
            maxConsecutiveSkips = 5, // Skip 5 runs = 5 minutes of inactivity
            checkIntervalAfterMaxSkips = 360000 // Check every 6 minutes after max skips
        )
    }

    /**
     * Runs every minute to close sessions that have started.
     * Uses adaptive scheduling: skips execution if no work found for 5+ consecutive runs.
     * This allows Neon DB to scale to 0 during idle periods.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    fun closeExpiredSessions() {
        val result = adaptiveTask.executeIfNeeded {
            val now = Instant.now()
            eventSessionRepository.updateStatusForExpiredSessions(
                oldStatus = SessionStatus.ON_SALE,
                newStatus = SessionStatus.SALES_CLOSED,
                now = now
            )
        }

        if (result.executed && result.workFound) {
            logger.info { "Closed ${result.workCount} expired sessions" }
        } else if (!result.executed) {
            // Skipped - no DB connection made, allowing Neon to scale to 0
            logger.trace { "Session close skipped (no work found in last ${result.skipCount} runs)" }
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
