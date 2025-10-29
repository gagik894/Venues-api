package app.venues.platform.repository

import app.venues.platform.domain.WebhookEvent
import app.venues.platform.domain.WebhookEventType
import app.venues.platform.domain.WebhookStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for WebhookEvent entity operations.
 */
@Repository
interface WebhookEventRepository : JpaRepository<WebhookEvent, UUID> {

    /**
     * Find webhook events by platform
     */
    fun findByPlatformId(platformId: Long, pageable: Pageable): Page<WebhookEvent>

    /**
     * Find webhook events by status
     */
    fun findByStatus(status: WebhookStatus, pageable: Pageable): Page<WebhookEvent>

    /**
     * Find webhook events by event type
     */
    fun findByEventType(eventType: WebhookEventType, pageable: Pageable): Page<WebhookEvent>

    /**
     * Find webhooks pending retry
     */
    @Query(
        """
        SELECT w FROM WebhookEvent w
        WHERE w.status = 'PENDING'
        AND w.attemptCount < :maxAttempts
        AND (w.nextRetryAt IS NULL OR w.nextRetryAt <= :now)
        ORDER BY w.createdAt ASC
    """
    )
    fun findPendingRetries(now: Instant, maxAttempts: Int = 5, pageable: Pageable): Page<WebhookEvent>

    /**
     * Find failed webhooks for a platform
     */
    fun findByPlatformIdAndStatus(
        platformId: Long,
        status: WebhookStatus,
        pageable: Pageable
    ): Page<WebhookEvent>

    /**
     * Count webhooks by platform and status
     */
    fun countByPlatformIdAndStatus(platformId: Long, status: WebhookStatus): Long

    /**
     * Find recent webhook events for session
     */
    fun findBySessionIdOrderByCreatedAtDesc(
        sessionId: Long,
        pageable: Pageable
    ): Page<WebhookEvent>

    /**
     * Delete old webhook events (cleanup)
     */
    fun deleteByCreatedAtBefore(before: Instant): Int
}

