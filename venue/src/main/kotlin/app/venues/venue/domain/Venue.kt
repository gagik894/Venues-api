package app.venues.venue.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*

/**
 * Represents a Venue (the "place").
 * This entity is "clean" and contains no authentication fields.
 * Auth is handled by the separate `Staff` entity.
 *
 * @param name The official name of the venue.
 * @param address The full street address of the venue.
 * @param description A detailed description of the venue.
 * @param imageUrl URL to the main image representing the venue.
 * @param city The city where the venue is located.
 * @param latitude The latitude coordinate of the venue.
 * @param longitude The longitude coordinate of the venue.
 * @param phoneNumber Contact phone number for the venue.
 * @param website Official website URL of the venue.
 * @param customDomain Custom domain name for the venue's page.
 * @param category The category/type of the venue (e.g., restaurant, gym).
 * @param isAlwaysOpen Indicates if the venue is open 24/7.
 * @param verificationDocumentUrl URL to the document used for venue verification.
 */
@Entity
@Table(
    name = "venues",
    indexes = [
        Index(name = "idx_venue_city", columnList = "city"),
        Index(name = "idx_venue_category", columnList = "category"),
        Index(name = "idx_venue_verified", columnList = "verified"),
        Index(name = "idx_venue_status", columnList = "status")
    ]
)
class Venue(
    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "address", nullable = false, length = 500)
    var address: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null,

    @Column(name = "city", length = 100)
    var city: String? = null,

    @Column(name = "latitude")
    var latitude: Double? = null,

    @Column(name = "longitude")
    var longitude: Double? = null,

    @Column(name = "phone_number", length = 50)
    var phoneNumber: String? = null,

    @Column(name = "website", length = 500)
    var website: String? = null,

    @Column(name = "custom_domain", length = 255)
    var customDomain: String? = null,

    @Column(name = "category", length = 50)
    var category: String? = null,

    @Column(name = "is_always_open", nullable = false)
    var isAlwaysOpen: Boolean = false,

    @Column(name = "time_zone", nullable = false, length = 50)
    var timeZone: String = "Asia/Yerevan"

) : AbstractUuidEntity() {

    // --- Internal State (Encapsulated) ---
    @Column(name = "verified", nullable = false)
    @Access(AccessType.FIELD)
    var verified: Boolean = false
        protected set

    @Column(name = "official", nullable = false)
    @Access(AccessType.FIELD)
    var official: Boolean = false
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Access(AccessType.FIELD)
    var status: VenueStatus = VenueStatus.PENDING_APPROVAL
        protected set

    // --- Relationships ---

    /**
     * Sensitive configuration stored separately.
     * LAZY loading prevents accidental exposure of credentials.
     * Only load when explicitly needed for payment/email operations.
     */
    @OneToOne(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var settings: VenueSettings? = null

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val schedules: MutableList<VenueSchedule> = mutableListOf()

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val photos: MutableList<VenuePhoto> = mutableListOf()

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val reviews: MutableList<VenueReview> = mutableListOf()

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val followers: MutableList<VenueFollower> = mutableListOf()

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val promoCodes: MutableList<VenuePromoCode> = mutableListOf()

    @OneToMany(mappedBy = "venue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val translations: MutableList<VenueTranslation> = mutableListOf()

    // --- Public Behaviors ---
    fun getNameInLanguage(language: String): String {
        return translations.find { it.language == language }?.name ?: name
    }

    /**
     * Approves a venue that was pending.
     */
    fun approveVenue() {
        if (this.status == VenueStatus.PENDING_APPROVAL) {
            this.status = VenueStatus.ACTIVE
            this.verified = true
        }
    }

    /**
     * Suspends an active venue.
     */
    fun suspendVenue() {
        if (this.status == VenueStatus.ACTIVE) {
            this.status = VenueStatus.SUSPENDED
        }
    }
}