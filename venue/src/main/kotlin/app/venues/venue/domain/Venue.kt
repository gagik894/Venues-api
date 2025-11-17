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
    @Column(nullable = false, length = 255)
    var name: String,

    @Column(nullable = false, length = 500)
    var address: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(length = 500)
    var imageUrl: String? = null,

    @Column(length = 100)
    var city: String? = null,

    @Column(name = "latitude")
    var latitude: Double? = null,

    @Column(name = "longitude")
    var longitude: Double? = null,

    @Column(length = 50)
    var phoneNumber: String? = null,

    @Column(length = 500)
    var website: String? = null,

    @Column(length = 255)
    var customDomain: String? = null,

    @Column(length = 50)
    var category: String? = null,

    @Column(nullable = false)
    var isAlwaysOpen: Boolean = false,

    @Column(length = 500)
    var verificationDocumentUrl: String? = null,

    // --- Payment and SMTP fields ---
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
    @Column(length = 255)
    var smtpEmail: String? = null,
    @Column(length = 255)
    var smtpPassword: String? = null,
) : AbstractUuidEntity() {

    // --- Internal State (Encapsulated) ---
    @Column(nullable = false)
    @Access(AccessType.FIELD)
    private var _verified: Boolean = false

    val verified: Boolean
        get() = _verified

    @Column(nullable = false)
    @Access(AccessType.FIELD)
    private var _official: Boolean = false

    val official: Boolean
        get() = _official

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Access(AccessType.FIELD)
    private var _status: VenueStatus = VenueStatus.PENDING_APPROVAL

    val status: VenueStatus
        get() = _status

    // --- Relationships ---
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
        if (this._status == VenueStatus.PENDING_APPROVAL) {
            this._status = VenueStatus.ACTIVE
            this._verified = true
        }
    }

    /**
     * Suspends an active venue.
     */
    fun suspendVenue() {
        if (this._status == VenueStatus.ACTIVE) {
            this._status = VenueStatus.SUSPENDED
        }
    }
}