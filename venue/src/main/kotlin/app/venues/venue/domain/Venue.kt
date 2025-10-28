package app.venues.venue.domain

import app.venues.common.constants.AppConstants
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Venue Entity
 *
 * Represents a cultural venue (theater, opera house, museum, etc.) in the system.
 * This is the core entity for the venue module.
 *
 * Key Features:
 * - Multi-language support for name and description
 * - Geolocation with PostGIS support
 * - Operating schedule management
 * - Verification and official status
 * - Social features (followers, reviews)
 * - Integration with payment systems
 *
 * Relationships:
 * - One-to-Many: Photos, Reviews, Schedules, PromoCodes
 * - Many-to-Many: Followers (Users)
 *
 * Security:
 * - Password for venue owner authentication
 * - Separate email credentials for notifications
 * - Payment gateway credentials (encrypted in production)
 */
@Entity
@Table(
    name = "venues",
    indexes = [
        Index(name = "idx_venue_email", columnList = "email"),
        Index(name = "idx_venue_city", columnList = "city"),
        Index(name = "idx_venue_category", columnList = "category"),
        Index(name = "idx_venue_verified", columnList = "verified"),
        Index(name = "idx_venue_status", columnList = "status")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Venue(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // ===========================================
    // Basic Information (with translations)
    // ===========================================

    /**
     * Venue name - supports translations via VenueTranslation entity
     * This is the default/fallback name
     */
    @Column(nullable = false, length = 255)
    var name: String,

    /**
     * Venue description - supports translations via VenueTranslation entity
     * This is the default/fallback description
     */
    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    /**
     * Main image URL for the venue
     */
    @Column(length = 500)
    var imageUrl: String? = null,

    // ===========================================
    // Location Information
    // ===========================================

    /**
     * Full address of the venue
     */
    @Column(nullable = false, length = 500)
    var address: String,

    /**
     * Canonical city name in English (for filtering/searching)
     */
    @Column(length = 100)
    var city: String? = null,

    /**
     * Latitude coordinate (WGS84)
     * Valid range: -90 to 90 degrees
     */
    @Column(name = "latitude")
    var latitude: Double? = null,

    /**
     * Longitude coordinate (WGS84)
     * Valid range: -180 to 180 degrees
     */
    @Column(name = "longitude")
    var longitude: Double? = null,

    // ===========================================
    // Contact Information
    // ===========================================

    /**
     * Primary contact email (also used for venue owner login)
     */
    @Column(unique = true, nullable = false, length = 255)
    var email: String,

    /**
     * Contact phone number
     */
    @Column(length = 50)
    var phoneNumber: String? = null,

    /**
     * Venue website URL
     */
    @Column(length = 500)
    var website: String? = null,

    /**
     * Custom domain for the venue (if any)
     */
    @Column(length = 255)
    var customDomain: String? = null,

    // ===========================================
    // Classification
    // ===========================================

    /**
     * Venue category (e.g., "THEATER", "OPERA_HOUSE", "MUSEUM", "CONCERT_HALL")
     */
    @Column(length = 50)
    var category: String? = null,

    // ===========================================
    // Operating Schedule
    // ===========================================

    /**
     * Flag indicating if venue is always open (24/7)
     */
    @Column(nullable = false)
    var isAlwaysOpen: Boolean = false,

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var schedules: MutableList<VenueSchedule> = mutableListOf(),

    // ===========================================
    // Verification & Status
    // ===========================================

    /**
     * Verification status (verified by admin)
     */
    @Column(nullable = false)
    var verified: Boolean = false,

    /**
     * Official status (official venue account)
     */
    @Column(nullable = false)
    var official: Boolean = false,

    /**
     * URL to verification document (stored securely)
     */
    @Column(length = 500)
    var verificationDocumentUrl: String? = null,

    /**
     * Current status of the venue
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: VenueStatus = VenueStatus.PENDING_APPROVAL,

    // ===========================================
    // Authentication
    // ===========================================

    /**
     * Hashed password for venue owner authentication
     */
    @Column(nullable = false, length = 255)
    var passwordHash: String,

    /**
     * Email password for SMTP (if sending emails from venue's email)
     * NOTE: Should be encrypted at rest using database-level encryption
     */
    @Column(length = 255)
    var emailPassword: String? = null,

    /**
     * Secret email for internal communications
     */
    @Column(length = 255)
    var secretEmail: String? = null,

    // ===========================================
    // Payment Integration Credentials
    // NOTE: These credentials should be encrypted at rest using database-level encryption
    // ===========================================

    @Column(length = 255)
    var telcelPostponeBillIssuer: String? = null,

    @Column(length = 255)
    var idramRecAccount: String? = null,

    @Column(length = 255)
    var idramSecretKey: String? = null,

    @Column(length = 255)
    var telcelStoreKey: String? = null,

    @Column(length = 255)
    var arcaUsername: String? = null,

    @Column(length = 255)
    var arcaPassword: String? = null,

    @Column(length = 255)
    var converseMerchantId: String? = null,

    @Column(length = 255)
    var converseSecretKey: String? = null,

    // ===========================================
    // Notifications
    // ===========================================

    /**
     * Firebase Cloud Messaging token for push notifications
     */
    @Column(length = 500)
    var fcmToken: String? = null,

    // ===========================================
    // Social Features
    // ===========================================

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var photos: MutableList<VenuePhoto> = mutableListOf(),

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var reviews: MutableList<VenueReview> = mutableListOf(),

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var followers: MutableList<VenueFollower> = mutableListOf(),

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var promoCodes: MutableList<VenuePromoCode> = mutableListOf(),

    // ===========================================
    // Translations
    // ===========================================

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var translations: MutableList<VenueTranslation> = mutableListOf(),

    // ===========================================
    // Security & Auditing
    // ===========================================

    /**
     * Number of consecutive failed login attempts
     */
    @Column(nullable = false)
    var failedLoginAttempts: Int = 0,

    /**
     * Timestamp when account was locked due to failed attempts
     */
    @Column
    var accountLockedUntil: Instant? = null,

    /**
     * Last successful login timestamp
     */
    @Column
    var lastLoginAt: Instant? = null,

    /**
     * Email verification status
     */
    @Column(nullable = false)
    var emailVerified: Boolean = false,

    /**
     * Email verification token
     */
    @Column(length = 255)
    var emailVerificationToken: String? = null,

    /**
     * Token expiration time
     */
    @Column
    var emailVerificationTokenExpiresAt: Instant? = null,

    // ===========================================
    // Audit Fields
    // ===========================================

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    /**
     * Computed property for full name (for consistency with potential future name changes)
     */
    val fullName: String
        get() = name

    /**
     * Check if the account is currently locked
     */
    fun isAccountLocked(): Boolean {
        return accountLockedUntil?.let { it.isAfter(Instant.now()) } ?: false
    }

    /**
     * Increment failed login attempts and lock account if threshold is reached.
     * Uses AppConstants.Security.MAX_LOGIN_ATTEMPTS and LOCKOUT_DURATION_MINUTES.
     */
    fun incrementFailedLoginAttempts() {
        failedLoginAttempts++

        // Auto-lock if threshold reached
        if (failedLoginAttempts >= AppConstants.Security.MAX_LOGIN_ATTEMPTS) {
            accountLockedUntil = Instant.now().plusSeconds(AppConstants.Security.LOCKOUT_DURATION_MINUTES * 60)
        }
    }

    /**
     * Reset failed login attempts on successful login
     */
    fun resetFailedLoginAttempts() {
        failedLoginAttempts = 0
        accountLockedUntil = null
    }

    /**
     * Get translation for specific language or fall back to default
     */
    fun getNameInLanguage(language: String): String {
        return translations.find { it.language == language }?.name ?: name
    }

    /**
     * Get description for specific language or fall back to default
     */
    fun getDescriptionInLanguage(language: String): String? {
        return translations.find { it.language == language }?.description ?: description
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Venue

        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "Venue(id=$id, name='$name', email='$email', city=$city, status=$status)"
    }
}

