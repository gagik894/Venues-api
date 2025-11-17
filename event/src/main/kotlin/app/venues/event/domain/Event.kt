package app.venues.event.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.util.*

/**
 * Represents a top-level Event.
 * This is a "root" entity.
 *
 * @param title The default title of the event.
 * @param venueId The UUID of the Venue hosting this event.
 * @param category The EventCategory this event belongs to.
 * @param imgUrl The main image URL for the event.
 * @param secondaryImgUrls Additional image URLs for the event.
 * @param description The detailed description of the event.
 * @param location The physical location/address of the event.
 * @param latitude The latitude coordinate of the event location.
 * @param longitude The longitude coordinate of the event location.
 * @param tags A set of tags associated with the event.
 * @param priceRange The price range description for the event.
 * @param currency The currency code for the event pricing (default is "AMD").
 * @param seatingChartId The UUID of the SeatingChart for this event.
 */
@Entity
@Table(
    name = "events",
    indexes = [
        Index(name = "idx_event_venue_id", columnList = "venue_id"),
        Index(name = "idx_event_status", columnList = "status"),
        Index(name = "idx_event_category_id", columnList = "category_id")
    ]
)
class Event(
    @Column(name = "title", nullable = false, length = 255)
    var title: String,

    /**
     * The `Venue.id` (a UUID) of the venue hosting this event.
     * This is a cross-module link, so we store the ID.
     */
    @Column(name = "venue_id", nullable = false)
    var venueId: UUID,

    /**
     * The category this event belongs to.
     * This is an intra-module link.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: EventCategory? = null,

    @Column(name = "img_url", length = 500)
    var imgUrl: String? = null,

    @ElementCollection
    @CollectionTable(name = "event_secondary_images", joinColumns = [JoinColumn(name = "event_id")])
    @Column(name = "image_url", length = 500)
    var secondaryImgUrls: MutableList<String> = mutableListOf(),

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "location", length = 500)
    var location: String? = null,

    @Column(name = "latitude")
    var latitude: Double? = null,

    @Column(name = "longitude")
    var longitude: Double? = null,

    @ElementCollection
    @CollectionTable(name = "event_tags", joinColumns = [JoinColumn(name = "event_id")])
    @Column(name = "tag", length = 50)
    var tags: MutableSet<String> = mutableSetOf(),

    @Column(name = "price_range", length = 100)
    var priceRange: String? = null,

    @Column(name = "currency", length = 3)
    var currency: String = "AMD",

    /**
     * The `SeatingChart.id` (a UUID) for this event.
     * This is a cross-module link, so we store the ID.
     */
    @Column(name = "seating_chart_id")
    var seatingChartId: UUID? = null,
) : AbstractUuidEntity() {

    // --- Internal State (Encapsulated) ---
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    private var _status: EventStatus = EventStatus.DRAFT

    val status: EventStatus
        get() = _status

    // --- Relationships ---
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val sessions: MutableList<EventSession> = mutableListOf()

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val priceTemplates: MutableList<EventPriceTemplate> = mutableListOf()

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val translations: MutableList<EventTranslation> = mutableListOf()

    // --- Public Behaviors ---
    fun publish() {
        if (this._status == EventStatus.DRAFT) {
            this._status = EventStatus.UPCOMING
        }
    }

    fun cancel() {
        this._status = EventStatus.CANCELLED
    }

    fun isEditable(): Boolean {
        return _status == EventStatus.DRAFT || _status == EventStatus.UPCOMING
    }

    fun addTranslation(translation: EventTranslation) {
        translation.event = this
        this.translations.add(translation)
    }
}