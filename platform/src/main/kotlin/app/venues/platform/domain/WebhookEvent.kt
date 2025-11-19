package app.venues.platform.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * A "root" entity logging a single webhook delivery attempt.
 *
 * @param platformId The `Platform.id` (a UUID) this event is for.
 * @param eventType The type of event (e.g., "SEAT_RESERVED").
 * @param sessionId The `EventSession.id` (a UUID) this event pertains to.
 * @param payload The JSON payload sent.
 * @param seatCode An optional seat identifier related to the event.
 * @param gaAreaCode An optional level identifier related to the event.
 */
@Entity
@Table(
    name = "webhook_events",
    indexes = [
        Index(name = "idx_webhook_platform_id", columnList = "platform_id"),
        Index(name = "idx_webhook_status", columnList = "status"),
        Index(name = "idx_webhook_next_retry", columnList = "next_retry_at")
    ]
)
class WebhookEvent(
    @Column(name = "platform_id", nullable = false)
    var platformId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: WebhookEventType,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    var payload: String,

    @Column(name = "seat_code", length = 50)
    var seatCode: String? = null,

    @Column(name = "ga_area_code", length = 50)
    var gaAreaCode: String? = null,

    @Column(name = "table_code", length = 50)
    var tableCode: String? = null

) : AbstractUuidEntity() {

    companion object {
        const val MAX_RETRY_ATTEMPTS = 5

        fun calculateRetryDelay(attemptCount: Int): Long {
            return when (attemptCount) {
                1 -> 60L        // 1 minute
                2 -> 300L       // 5 minutes
                3 -> 900L       // 15 minutes
                4 -> 3600L      // 1 hour
                else -> 14400L  // 4 hours
            }
        }
    }

    // --- Internal State (Encapsulated) ---
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Access(AccessType.FIELD)
    var status: WebhookStatus = WebhookStatus.PENDING
        protected set

    @Column(name = "response_code")
    @Access(AccessType.FIELD)
    var responseCode: Int? = null
        protected set

    @Column(name = "response_body", columnDefinition = "TEXT")
    @Access(AccessType.FIELD)
    var responseBody: String? = null
        protected set

    @Column(name = "error_message", columnDefinition = "TEXT")
    @Access(AccessType.FIELD)
    var errorMessage: String? = null
        protected set

    @Column(name = "attempt_count", nullable = false)
    @Access(AccessType.FIELD)
    var attemptCount: Int = 0
        protected set

    @Column(name = "next_retry_at")
    @Access(AccessType.FIELD)
    var nextRetryAt: Instant? = null
        protected set

    @Column(name = "last_attempt_at")
    @Access(AccessType.FIELD)
    var lastAttemptAt: Instant? = null
        protected set

    // --- Public Behaviors ---

    /**
     * Marks the webhook as successfully delivered.
     */
    fun markAsDelivered(responseCode: Int, responseBody: String?) {
        this.status = WebhookStatus.DELIVERED
        this.responseCode = responseCode
        this.responseBody = responseBody
        this.lastAttemptAt = Instant.now()
        this.nextRetryAt = null
    }

    /**
     * Marks the webhook as failed and schedules a retry if applicable.
     */
    fun markAsFailed(responseCode: Int?, errorMessage: String?) {
        this.attemptCount++
        this.responseCode = responseCode
        this.errorMessage = errorMessage
        this.lastAttemptAt = Instant.now()

        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
            this.status = WebhookStatus.FAILED
            this.nextRetryAt = null
        } else {
            this.status = WebhookStatus.PENDING
            val delaySeconds = calculateRetryDelay(attemptCount)
            this.nextRetryAt = Instant.now().plusSeconds(delaySeconds)
        }
    }

    fun shouldRetry(): Boolean {
        return status == WebhookStatus.PENDING
                && attemptCount < MAX_RETRY_ATTEMPTS
                && (nextRetryAt == null || Instant.now().isAfter(nextRetryAt))
    }
}
