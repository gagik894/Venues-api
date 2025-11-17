package app.venues.platform.domain

import app.venues.common.domain.AbstractUuidEntity
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
 * @param seatIdentifier An optional seat identifier related to the event.
 * @param levelIdentifier An optional level identifier related to the event.
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

    @Column(name = "seat_identifier", length = 50)
    var seatIdentifier: String? = null,

    @Column(name = "level_identifier", length = 50)
    var levelIdentifier: String? = null,

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
    private var _status: WebhookStatus = WebhookStatus.PENDING

    val status: WebhookStatus
        get() = _status

    @Column(name = "response_code")
    @Access(AccessType.FIELD)
    private var _responseCode: Int? = null

    val responseCode: Int?
        get() = _responseCode

    @Column(name = "response_body", columnDefinition = "TEXT")
    @Access(AccessType.FIELD)
    private var _responseBody: String? = null

    val responseBody: String?
        get() = _responseBody

    @Column(name = "error_message", columnDefinition = "TEXT")
    @Access(AccessType.FIELD)
    private var _errorMessage: String? = null

    val errorMessage: String?
        get() = _errorMessage

    @Column(name = "attempt_count", nullable = false)
    @Access(AccessType.FIELD)
    private var _attemptCount: Int = 0

    val attemptCount: Int
        get() = _attemptCount

    @Column(name = "next_retry_at")
    @Access(AccessType.FIELD)
    private var _nextRetryAt: Instant? = null

    val nextRetryAt: Instant?
        get() = _nextRetryAt

    @Column(name = "last_attempt_at")
    @Access(AccessType.FIELD)
    private var _lastAttemptAt: Instant? = null

    val lastAttemptAt: Instant?
        get() = _lastAttemptAt

    // --- Public Behaviors ---

    /**
     * Marks the webhook as successfully delivered.
     */
    fun markAsDelivered(responseCode: Int, responseBody: String?) {
        this._status = WebhookStatus.DELIVERED
        this._responseCode = responseCode
        this._responseBody = responseBody
        this._lastAttemptAt = Instant.now()
        this._nextRetryAt = null
    }

    /**
     * Marks the webhook as failed and schedules a retry if applicable.
     */
    fun markAsFailed(responseCode: Int?, errorMessage: String?) {
        this._attemptCount++
        this._responseCode = responseCode
        this._errorMessage = errorMessage
        this._lastAttemptAt = Instant.now()

        if (_attemptCount >= MAX_RETRY_ATTEMPTS) {
            this._status = WebhookStatus.FAILED
            this._nextRetryAt = null
        } else {
            this._status = WebhookStatus.PENDING
            val delaySeconds = calculateRetryDelay(_attemptCount)
            this._nextRetryAt = Instant.now().plusSeconds(delaySeconds)
        }
    }

    fun shouldRetry(): Boolean {
        return _status == WebhookStatus.PENDING
                && _attemptCount < MAX_RETRY_ATTEMPTS
                && (_nextRetryAt == null || Instant.now().isAfter(_nextRetryAt))
    }
}