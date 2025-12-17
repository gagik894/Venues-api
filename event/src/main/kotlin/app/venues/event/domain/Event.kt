package app.venues.event.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.*
import java.time.Instant
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
        Index(name = "idx_event_category_id", columnList = "category_id"),
        Index(name = "idx_event_first_session_start", columnList = "first_session_start"),
        Index(name = "idx_event_last_session_end", columnList = "last_session_end")
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
    @org.hibernate.annotations.BatchSize(size = 100)
    var secondaryImgUrls: MutableSet<String> = mutableSetOf(),

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
    @org.hibernate.annotations.BatchSize(size = 100)
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

    /**
     * Override Merchant Profile for this specific event.
     * Used for co-hosted events or specific financial routing.
     */
    @Column(name = "merchant_profile_id")
    var merchantProfileId: UUID? = null,

    /**
     * The start time of the earliest session.
     * Used for sorting events by "Soonest".
     */
    @Column(name = "first_session_start")
    var firstSessionStart: Instant? = null,

    /**
     * The end time of the latest session.
     * Used for filtering "Ongoing" events.
     */
    @Column(name = "last_session_end")
    var lastSessionEnd: Instant? = null,
) : AbstractUuidEntity() {

    // --- Internal State (Encapsulated) ---
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Access(AccessType.FIELD)
    var status: EventStatus = EventStatus.DRAFT
        protected set

    // --- Relationships ---
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("startTime ASC")
    @org.hibernate.annotations.BatchSize(size = 100)
    val sessions: MutableList<EventSession> = mutableListOf()

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("price ASC")
    @org.hibernate.annotations.BatchSize(size = 100)
    val priceTemplates: MutableSet<EventPriceTemplate> = mutableSetOf()

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 100)
    val translations: MutableSet<EventTranslation> = mutableSetOf()

    // --- Public Behaviors ---

    /**
     * Publishes a draft event, making it visible to the public.
     * Can only transition from DRAFT to PUBLISHED.
     *
     * @throws IllegalStateException if event is not in DRAFT status.
     */
    fun publish() {
        require(status == EventStatus.DRAFT) {
            "Cannot publish event: current status is $status, must be DRAFT"
        }
        this.status = EventStatus.PUBLISHED
    }

    /**
     * Suspends a published event, temporarily removing it from public visibility.
     * Can only transition from PUBLISHED to SUSPENDED.
     *
     * @throws IllegalStateException if event is not in PUBLISHED status.
     */
    fun suspend() {
        require(status == EventStatus.PUBLISHED) {
            "Cannot suspend event: current status is $status, must be PUBLISHED"
        }
        this.status = EventStatus.SUSPENDED
    }

    /**
     * Resumes a suspended event, returning it to public visibility.
     * Can only transition from SUSPENDED to PUBLISHED.
     *
     * @throws IllegalStateException if event is not in SUSPENDED status.
     */
    fun resume() {
        require(status == EventStatus.SUSPENDED) {
            "Cannot resume event: current status is $status, must be SUSPENDED"
        }
        this.status = EventStatus.PUBLISHED
    }

    /**
     * Archives a completed event.
     * Can only transition from PUBLISHED to ARCHIVED.
     * Typically called automatically by scheduler for past events.
     *
     * @throws IllegalStateException if event is not in PUBLISHED status.
     */
    fun archive() {
        require(status == EventStatus.PUBLISHED) {
            "Cannot archive event: current status is $status, must be PUBLISHED"
        }
        this.status = EventStatus.ARCHIVED
    }

    /**
     * Soft-deletes the event.
     * Can be called from any status except already DELETED.
     *
     * @throws IllegalStateException if event is already deleted.
     */
    fun markAsDeleted() {
        require(status != EventStatus.DELETED) {
            "Event is already deleted"
        }
        this.status = EventStatus.DELETED
    }

    /**
     * Checks if the event status can be edited by staff.
     * Events in DRAFT and PUBLISHED status are editable.
     *
     * @return true if editable, false otherwise.
     */
    fun isEditable(): Boolean {
        return status == EventStatus.DRAFT || status == EventStatus.PUBLISHED
    }

    /**
     * Checks if status transition is allowed from current status to target status.
     *
     * @param targetStatus The desired target status.
     * @return true if transition is allowed, false otherwise.
     */
    fun canTransitionTo(targetStatus: EventStatus): Boolean {
        if (status == targetStatus) return false

        return when (status) {
            EventStatus.DRAFT -> targetStatus in setOf(EventStatus.PUBLISHED, EventStatus.DELETED)
            EventStatus.PUBLISHED -> targetStatus in setOf(
                EventStatus.SUSPENDED,
                EventStatus.ARCHIVED,
                EventStatus.DELETED
            )

            EventStatus.SUSPENDED -> targetStatus in setOf(EventStatus.PUBLISHED, EventStatus.DELETED)
            EventStatus.ARCHIVED -> targetStatus == EventStatus.DELETED
            EventStatus.DELETED -> false
        }
    }

    /**
     * Transitions event to the target status with validation.
     *
     * @param targetStatus The desired status to transition to.
     * @throws IllegalStateException if transition is not allowed.
     */
    fun transitionTo(targetStatus: EventStatus) {
        require(canTransitionTo(targetStatus)) {
            "Cannot transition from $status to $targetStatus"
        }

        when (targetStatus) {
            EventStatus.PUBLISHED -> if (status == EventStatus.DRAFT) publish() else resume()
            EventStatus.SUSPENDED -> suspend()
            EventStatus.ARCHIVED -> archive()
            EventStatus.DELETED -> markAsDeleted()
            EventStatus.DRAFT -> throw IllegalStateException("Cannot transition back to DRAFT")
        }
    }

    /**
     * Adds a translation to the event.
     *
     * @param translation The translation to add.
     */
    fun addTranslation(translation: EventTranslation) {
        translation.event = this
        this.translations.add(translation)
    }
}
