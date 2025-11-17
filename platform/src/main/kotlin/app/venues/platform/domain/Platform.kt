package app.venues.platform.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * A "root" entity representing an external API platform (e.g., "Ticketmaster").
 *
 * @param name A unique name for the platform.
 * @param apiUrl The base URL for sending webhooks to the platform.
 * @param sharedSecret A secret used to sign webhook payloads.
 * @param description An optional description of the platform.
 * @param contactEmail An optional contact email for platform-related issues.
 * @param rateLimit An optional rate limit (requests per minute) for API calls.
 */
@Entity
@Table(name = "platforms")
class Platform(
    @Column(name = "name", nullable = false, unique = true, length = 100)
    var name: String,

    @Column(name = "api_url", nullable = false, length = 500)
    var apiUrl: String,

    @Column(name = "shared_secret", nullable = false, length = 255)
    var sharedSecret: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null,

    @Column(name = "rate_limit")
    var rateLimit: Int? = null,

    ) : AbstractUuidEntity() {

    // --- Internal State (Encapsulated) ---
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Access(AccessType.FIELD)
    private var _status: PlatformStatus = PlatformStatus.ACTIVE

    val status: PlatformStatus
        get() = _status

    @Column(name = "webhook_enabled", nullable = false)
    @Access(AccessType.FIELD)
    private var _webhookEnabled: Boolean = true

    val webhookEnabled: Boolean
        get() = _webhookEnabled

    @Column(name = "last_webhook_success")
    @Access(AccessType.FIELD)
    private var _lastWebhookSuccess: Instant? = null

    val lastWebhookSuccess: Instant?
        get() = _lastWebhookSuccess

    @Column(name = "last_webhook_failure")
    @Access(AccessType.FIELD)
    private var _lastWebhookFailure: Instant? = null

    val lastWebhookFailure: Instant?
        get() = _lastWebhookFailure

    @Column(name = "webhook_success_count", nullable = false)
    @Access(AccessType.FIELD)
    private var _webhookSuccessCount: Long = 0

    val webhookSuccessCount: Long
        get() = _webhookSuccessCount

    @Column(name = "webhook_failure_count", nullable = false)
    @Access(AccessType.FIELD)
    private var _webhookFailureCount: Long = 0

    val webhookFailureCount: Long
        get() = _webhookFailureCount

    // --- Public Behaviors ---
    fun isActive(): Boolean = _status == PlatformStatus.ACTIVE

    fun shouldReceiveWebhooks(): Boolean = _webhookEnabled && isActive()

    fun deactivate() {
        this._status = PlatformStatus.INACTIVE
    }

    fun enableWebhooks() {
        this._webhookEnabled = true
    }

    fun disableWebhooks() {
        this._webhookEnabled = false
    }

    fun recordWebhookSuccess() {
        this._lastWebhookSuccess = Instant.now()
        this._webhookSuccessCount++
    }

    fun recordWebhookFailure() {
        this._lastWebhookFailure = Instant.now()
        this._webhookFailureCount++
    }
}