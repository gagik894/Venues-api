package app.venues.platform.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Platform entity representing external systems that can integrate with our booking API.
 *
 * Platforms can:
 * - Reserve seats through our API using API authentication
 * - Receive webhook callbacks when seat availability changes
 * - Optionally provide guest details or book anonymously
 *
 * @property id Platform unique identifier
 * @property name Platform name (e.g., "Ticketmaster", "StubHub", "PartnerApp")
 * @property apiUrl Base URL for platform's webhook endpoints
 * @property sharedSecret Secret key for HMAC signature validation of callbacks
 * @property status Current status of the platform integration
 * @property webhookEnabled Whether to send webhooks to this platform
 * @property description Optional description of the platform
 * @property contactEmail Technical contact email for the platform
 * @property rateLimit Maximum API requests per minute (null = no limit)
 * @property createdAt Platform creation timestamp
 * @property lastModifiedAt Last update timestamp
 */
@Entity
@Table(
    name = "platforms",
    indexes = [
        Index(name = "idx_platform_status", columnList = "status"),
        Index(name = "idx_platform_webhook_enabled", columnList = "webhook_enabled")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Platform(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * Platform name (must be unique)
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    var name: String,

    /**
     * Base URL for platform's API/webhooks (e.g., "https://partner.example.com/api")
     */
    @Column(name = "api_url", nullable = false, length = 500)
    var apiUrl: String,

    /**
     * Shared secret for HMAC signature validation
     * Used to sign webhook payloads
     */
    @Column(name = "shared_secret", nullable = false, length = 255)
    var sharedSecret: String,

    /**
     * Platform status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PlatformStatus = PlatformStatus.ACTIVE,

    /**
     * Whether to send webhook callbacks to this platform
     */
    @Column(name = "webhook_enabled", nullable = false)
    var webhookEnabled: Boolean = true,

    /**
     * Platform description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    /**
     * Technical contact email
     */
    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null,

    /**
     * Rate limit (requests per minute, null = unlimited)
     */
    @Column(name = "rate_limit")
    var rateLimit: Int? = null,

    /**
     * Last successful webhook delivery timestamp
     */
    @Column(name = "last_webhook_success")
    var lastWebhookSuccess: Instant? = null,

    /**
     * Last failed webhook delivery timestamp
     */
    @Column(name = "last_webhook_failure")
    var lastWebhookFailure: Instant? = null,

    /**
     * Total successful webhooks sent
     */
    @Column(name = "webhook_success_count", nullable = false)
    var webhookSuccessCount: Long = 0,

    /**
     * Total failed webhooks
     */
    @Column(name = "webhook_failure_count", nullable = false)
    var webhookFailureCount: Long = 0,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    /**
     * Check if platform is active and can make reservations
     */
    fun isActive(): Boolean = status == PlatformStatus.ACTIVE

    /**
     * Check if webhooks should be sent to this platform
     */
    fun shouldReceiveWebhooks(): Boolean = webhookEnabled && isActive()
}

/**
 * Platform integration status
 */
enum class PlatformStatus {
    /**
     * Platform is active and can make reservations
     */
    ACTIVE,

    /**
     * Platform is temporarily suspended (can't make new reservations)
     */
    SUSPENDED,

    /**
     * Platform is inactive (disabled by admin)
     */
    INACTIVE
}

