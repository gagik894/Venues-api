package app.venues.platform.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.*

/**
 * WebhookEvent entity for tracking webhook delivery attempts.
 *
 * Records all webhook callbacks sent to platforms when seat availability changes.
 *
 * @property id Webhook event unique identifier
 * @property platform The platform receiving the webhook
 * @property eventType Type of event (e.g., SEAT_RESERVED, SEAT_RELEASED)
 * @property sessionId Event session ID
 * @property seatIdentifier Seat identifier (if applicable)
 * @property levelIdentifier Level identifier for GA tickets (if applicable)
 * @property payload JSON payload sent to platform
 * @property status Delivery status
 * @property responseCode HTTP response code from platform
 * @property responseBody Response body from platform
 * @property errorMessage Error message if delivery failed
 * @property attemptCount Number of delivery attempts
 * @property nextRetryAt Next scheduled retry time (for failed deliveries)
 * @property createdAt Event creation timestamp
 */
@Entity
@Table(
    name = "webhook_events",
    indexes = [
        Index(name = "idx_webhook_platform_id", columnList = "platform_id"),
        Index(name = "idx_webhook_status", columnList = "status"),
        Index(name = "idx_webhook_event_type", columnList = "event_type"),
        Index(name = "idx_webhook_session_id", columnList = "session_id"),
        Index(name = "idx_webhook_next_retry", columnList = "next_retry_at"),
        Index(name = "idx_webhook_created_at", columnList = "created_at")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class WebhookEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    /**
     * Platform receiving the webhook
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_id", nullable = false)
    var platform: Platform,

    /**
     * Event type
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: WebhookEventType,

    /**
     * Event session ID
     */
    @Column(name = "session_id", nullable = false)
    var sessionId: Long,

    /**
     * Seat identifier (for seat-specific events)
     */
    @Column(name = "seat_identifier", length = 50)
    var seatIdentifier: String? = null,

    /**
     * Level identifier (for GA events)
     */
    @Column(name = "level_identifier", length = 50)
    var levelIdentifier: String? = null,

    /**
     * JSON payload sent to platform
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    var payload: String,

    /**
     * Delivery status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: WebhookStatus = WebhookStatus.PENDING,

    /**
     * HTTP response code from platform
     */
    @Column(name = "response_code")
    var responseCode: Int? = null,

    /**
     * Response body from platform
     */
    @Column(name = "response_body", columnDefinition = "TEXT")
    var responseBody: String? = null,

    /**
     * Error message if delivery failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    /**
     * Number of delivery attempts
     */
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    /**
     * Next scheduled retry time (for failed deliveries)
     */
    @Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null,

    /**
     * Last attempt timestamp
     */
    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    companion object {
        const val MAX_RETRY_ATTEMPTS = 5

        /**
         * Calculate next retry delay using exponential backoff
         * 1st retry: 1 minute
         * 2nd retry: 5 minutes
         * 3rd retry: 15 minutes
         * 4th retry: 1 hour
         * 5th retry: 4 hours
         */
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

    /**
     * Mark webhook as delivered successfully
     */
    fun markAsDelivered(responseCode: Int, responseBody: String?) {
        this.status = WebhookStatus.DELIVERED
        this.responseCode = responseCode
        this.responseBody = responseBody
        this.lastAttemptAt = Instant.now()
    }

    /**
     * Mark webhook as failed
     */
    fun markAsFailed(responseCode: Int?, errorMessage: String) {
        this.attemptCount++
        this.status = if (attemptCount >= MAX_RETRY_ATTEMPTS) {
            WebhookStatus.FAILED
        } else {
            WebhookStatus.PENDING
        }
        this.responseCode = responseCode
        this.errorMessage = errorMessage
        this.lastAttemptAt = Instant.now()

        // Schedule retry if not max attempts
        if (attemptCount < MAX_RETRY_ATTEMPTS) {
            val delaySeconds = calculateRetryDelay(attemptCount)
            this.nextRetryAt = Instant.now().plusSeconds(delaySeconds)
        }
    }

    /**
     * Check if webhook should be retried
     */
    fun shouldRetry(): Boolean {
        return status == WebhookStatus.PENDING
                && attemptCount < MAX_RETRY_ATTEMPTS
                && (nextRetryAt == null || Instant.now().isAfter(nextRetryAt))
    }
}

/**
 * Webhook event types
 */
enum class WebhookEventType {
    /**
     * Seat was reserved (added to cart)
     */
    SEAT_RESERVED,

    /**
     * Seat reservation was released (removed from cart or expired)
     */
    SEAT_RELEASED,

    /**
     * Seat was booked (payment confirmed)
     */
    SEAT_BOOKED,

    /**
     * Booking was cancelled (seat becomes available again)
     */
    BOOKING_CANCELLED,

    /**
     * GA tickets availability changed
     */
    GA_AVAILABILITY_CHANGED,

    /**
     * Session seat configuration updated
     */
    SESSION_CONFIG_UPDATED
}

/**
 * Webhook delivery status
 */
enum class WebhookStatus {
    /**
     * Webhook is pending delivery or retry
     */
    PENDING,

    /**
     * Webhook was successfully delivered
     */
    DELIVERED,

    /**
     * Webhook delivery failed after max retries
     */
    FAILED
}

