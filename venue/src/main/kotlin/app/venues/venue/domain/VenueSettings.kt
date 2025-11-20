package app.venues.venue.domain


import app.venues.venue.converter.SmtpConfigConverter
import app.venues.venue.dto.SmtpConfig
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.*

/**
 * Stores sensitive venue configuration separately from main Venue entity.
 *
 * Security benefits:
 * - Sensitive data not loaded when querying venue lists
 * - Lazy loading prevents accidental exposure in logs
 * - Encrypted JSON storage protects credentials at rest
 * - Easy to add new payment providers without schema changes
 *
 * Configuration is stored as encrypted JSON blobs to support:
 * - Multiple payment gateways (Idram, Telcel, Arca, Converse, Stripe, etc.)
 * - SMTP credentials
 * - Future integrations without database migrations
 *
 * Uses @MapsId to share primary key with Venue (no separate ID generation).
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "venue_settings")
class VenueSettings(
    /**
     * One-to-one relationship with Venue.
     * LAZY loading ensures settings are only loaded when explicitly needed.
     * @MapsId tells Hibernate: "Use the venue's ID as my primary key"
     */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "venue_id")
    var venue: Venue,

    /**
     * SMTP configuration as encrypted JSON.
     * Automatically encrypted/decrypted by JPA AttributeConverter.
     *
     * Example structure (before encryption):
     * {
     *   "email": "notifications@venue.com",
     *   "password": "...",
     *   "host": "smtp.gmail.com",
     *   "port": 587
     * }
     */
    @Column(name = "smtp_config_json", columnDefinition = "TEXT")
    @Convert(converter = SmtpConfigConverter::class)
    var smtpConfig: SmtpConfig? = null,

    /**
     * Additional custom configuration as encrypted JSON.
     * Allows venues to store custom integration settings.
     */
    @Column(name = "custom_config_json", columnDefinition = "TEXT")
    var customConfigJson: String? = null

) {
    /**
     * Shared primary key with Venue.
     * This is the venue's UUID, not a separate generated ID.
     */
    @Id
    @Column(name = "venue_id")
    var id: UUID? = null

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()

    @PreUpdate
    fun onUpdate() {
        lastModifiedAt = Instant.now()
    }

    /**
     * Check if SMTP is configured.
     */
    fun hasSmtpConfig(): Boolean = smtpConfig != null

    /**
     * Clear all sensitive configuration data.
     * Use when venue is being deleted or deactivated.
     */
    fun clearAllSecrets() {

        smtpConfig = null
        customConfigJson = null
    }
}

