package app.venues.venue.domain

import app.venues.location.domain.City
import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.*

/**
 * Represents a Venue (the "place").
 * This entity is "clean" and contains no authentication fields.
 * Auth is handled by the separate `Staff` entity.
 *
 * @param name The official name of the venue.
 * @param address The full street address of the venue.
 * @param description A detailed description of the venue.
 * @param logoUrl URL to the main image representing the venue.
 * @param city The city where the venue is located.
 * @param latitude The latitude coordinate of the venue.
 * @param longitude The longitude coordinate of the venue.
 * @param phoneNumber Contact phone number for the venue.
 * @param website Official website URL of the venue.
 * @param customDomain Custom domain name for the venue's page.
 * @param category The category/type of the venue (e.g., restaurant, gym).
 * @param isAlwaysOpen Indicates if the venue is open 24/7.
 */
@Entity
@Table(
    name = "venues",
    indexes = [
        Index(name = "idx_venue_city", columnList = "city_id"),
        Index(name = "idx_venue_slug", columnList = "slug", unique = true),
        Index(name = "idx_venue_status", columnList = "status")
    ]
)
class Venue(

    // --- Base Content (Default Language) ---
    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "slug", nullable = false, unique = true)
    var slug: String,

    // --- Legal ---

    /**
     * The Parent Organization ID.
     * Allows efficient "Get all venues for Ministry X" queries.
     */
    @Column(name = "organization_id", nullable = false)
    var organizationId: UUID,

    @Column(name = "legal_name", length = 255)
    var legalName: String? = null,

    @Column(name = "tax_id", length = 50) // Essential for Gov/Invoicing
    var taxId: String? = null,

    /**
     * Override Merchant Profile for this specific venue.
     * If null, falls back to Organization default.
     */
    @Column(name = "merchant_profile_id")
    var merchantProfileId: UUID? = null,

    // --- Visuals (needed for lists, so keep in Main Table) ---
    @Column(name = "logo_url", length = 500)
    var logoUrl: String? = null,

    @Column(name = "cover_image_url", length = 500)
    var coverImageUrl: String? = null,

    // --- Location ---
    @Column(name = "address", nullable = false, length = 500)
    var address: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    var city: City,

    @Column(name = "time_zone", nullable = false)
    var timeZone: String = "Asia/Yerevan",

    @Column(name = "latitude")
    var latitude: Double? = null,

    @Column(name = "longitude")
    var longitude: Double? = null,

    // --- Config ---
    @Column(name = "is_always_open")
    var isAlwaysOpen: Boolean = false,

    @Column(name = "custom_domain")
    var customDomain: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: VenueCategory? = null,

    // --- Contacts ---
    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @Column(name = "website")
    var website: String? = null,

    @Column(name = "contact_email")
    var contactEmail: String? = null,

    @Column(name = "social_links", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var socialLinks: Map<String, String>? = null,

    /**
     * Ownership type (State vs Private).
     * Critical for Government reporting and tax handling.
     */
    @Column(name = "ownership_type", length = 20)
    @Enumerated(EnumType.STRING)
    var ownershipType: VenueOwnership? = null,

    /**
     * Private list of emails that receive system alerts (Sales reports, Payout issues).
     * JSONB List: ["manager@opera.am", "accountant@opera.am"]
     */
    @Column(name = "notification_emails", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var notificationEmails: List<String> = emptyList(),
) : AbstractUuidEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: VenueStatus = VenueStatus.PENDING_APPROVAL
        protected set

    // --- Relations ---

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var translations: MutableList<VenueTranslation> = mutableListOf()

    @OneToOne(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var branding: VenueBranding? = null // The White Label Config

    @OneToOne(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    var settings: VenueSettings? = null // The Secrets (Stripe/SMTP)

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("dayOfWeek ASC")
    var schedules: MutableList<VenueSchedule> = mutableListOf()

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    var photos: MutableList<VenuePhoto> = mutableListOf()

    // Helper
    fun getName(lang: String): String =
        translations.firstOrNull { it.language == lang }?.name ?: name

    fun getDescription(lang: String): String? =
        translations.firstOrNull { it.language == lang }?.description ?: description

    fun activate() {
        this.status = VenueStatus.ACTIVE
    }

    fun suspend() {
        this.status = VenueStatus.SUSPENDED
    }

    fun delete() {
        this.status = VenueStatus.DELETED
    }
}