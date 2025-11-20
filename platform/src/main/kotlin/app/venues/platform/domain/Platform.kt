package app.venues.platform.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
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
    var status: PlatformStatus = PlatformStatus.ACTIVE
        protected set

    @Column(name = "webhook_enabled", nullable = false)
    @Access(AccessType.FIELD)
    var webhookEnabled: Boolean = true
        protected set

    @Column(name = "last_webhook_success")
    @Access(AccessType.FIELD)
    var lastWebhookSuccess: Instant? = null
        protected set

    @Column(name = "last_webhook_failure")
    @Access(AccessType.FIELD)
    var lastWebhookFailure: Instant? = null
        protected set

    @Column(name = "webhook_success_count", nullable = false)
    @Access(AccessType.FIELD)
    var webhookSuccessCount: Long = 0
        protected set

    @Column(name = "webhook_failure_count", nullable = false)
    @Access(AccessType.FIELD)
    var webhookFailureCount: Long = 0
        protected set

    // --- Public Behaviors ---
    fun isActive(): Boolean = status == PlatformStatus.ACTIVE

    fun shouldReceiveWebhooks(): Boolean = webhookEnabled && isActive()

    fun deactivate() {
        this.status = PlatformStatus.INACTIVE
    }

    fun enableWebhooks() {
        this.webhookEnabled = true
    }

    fun disableWebhooks() {
        this.webhookEnabled = false
    }

    fun recordWebhookSuccess() {
        this.lastWebhookSuccess = Instant.now()
        this.webhookSuccessCount++
    }

    fun recordWebhookFailure() {
        this.lastWebhookFailure = Instant.now()
        this.webhookFailureCount++
    }
}
