package app.venues.event.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Main Event entity representing a cultural event.
 *
 * An event is hosted by a venue and can have multiple sessions (time slots).
 * Events support translations for international audiences.
 *
 * Cross-module relationships:
 * - venueId references venue module
 * - seatingChartId references seating module
 */
@Entity
@Table(
    name = "events",
    indexes = [
        Index(name = "idx_event_venue_id", columnList = "venue_id"),
        Index(name = "idx_event_status", columnList = "status"),
        Index(name = "idx_event_category_id", columnList = "category_id"),
        Index(name = "idx_event_created_at", columnList = "created_at")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Event(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // ===========================================
    // Basic Information
    // ===========================================

    /**
     * Event title (default language)
     */
    @Column(nullable = false, length = 255)
    var title: String,

    /**
     * Primary image URL for the event
     */
    @Column(name = "img_url", length = 500)
    var imgUrl: String? = null,

    /**
     * Secondary/additional images (stored as JSON array or separate table)
     */
    @ElementCollection
    @CollectionTable(name = "event_secondary_images", joinColumns = [JoinColumn(name = "event_id")])
    @Column(name = "image_url", length = 500)
    var secondaryImgUrls: MutableList<String> = mutableListOf(),

    /**
     * Event description (default language)
     */
    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    // ===========================================
    // Venue & Location
    // ===========================================

    /**
     * Venue ID - references venue module
     * Stored as ID to avoid cross-module entity dependencies
     */
    @Column(name = "venue_id", nullable = false)
    var venueId: Long,

    /**
     * Specific location/address if different from venue's main address
     */
    @Column(length = 500)
    var location: String? = null,

    /**
     * Latitude for event-specific location
     */
    @Column(name = "latitude")
    var latitude: Double? = null,

    /**
     * Longitude for event-specific location
     */
    @Column(name = "longitude")
    var longitude: Double? = null,

    // ===========================================
    // Categorization & Tags
    // ===========================================

    /**
     * Event category (references EventCategory table within same module)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: EventCategory? = null,

    /**
     * Tags for searchability and filtering
     */
    @ElementCollection
    @CollectionTable(name = "event_tags", joinColumns = [JoinColumn(name = "event_id")])
    @Column(name = "tag", length = 50)
    var tags: MutableSet<String> = mutableSetOf(),

    // ===========================================
    // Pricing
    // ===========================================

    /**
     * Price range display (e.g., "$20 - $100", "Free", "From $50")
     */
    @Column(name = "price_range", length = 100)
    var priceRange: String? = null,

    /**
     * Currency code (ISO 4217: USD, EUR, AMD, etc.)
     */
    @Column(length = 3)
    var currency: String = "AMD",

    /**
     * Seating chart ID - references seating module
     * Stored as ID to avoid cross-module entity dependencies
     * Optional - for seated events only
     */
    @Column(name = "seating_chart_id")
    var seatingChartId: Long? = null,

    // ===========================================
    // Status & State
    // ===========================================

    /**
     * Current status of the event
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: EventStatus = EventStatus.DRAFT,

    // ===========================================
    // Relationships
    // ===========================================

    /**
     * Event sessions (time slots)
     */
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var sessions: MutableList<EventSession> = mutableListOf(),

    /**
     * Price templates for different ticket types
     */
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var priceTemplates: MutableList<EventPriceTemplate> = mutableListOf(),

    /**
     * Translations for title and description
     */
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var translations: MutableList<EventTranslation> = mutableListOf(),

    // ===========================================
    // Audit Fields
    // ===========================================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    /**
     * Helper method to add a session
     */
    fun addSession(session: EventSession) {
        sessions.add(session)
        session.event = this
    }

    /**
     * Helper method to add a price template
     */
    fun addPriceTemplate(template: EventPriceTemplate) {
        priceTemplates.add(template)
        template.event = this
    }

    /**
     * Helper method to add a translation
     */
    fun addTranslation(translation: EventTranslation) {
        translations.add(translation)
        translation.event = this
    }

    /**
     * Check if event is editable (only draft/upcoming can be edited)
     */
    fun isEditable(): Boolean {
        return status == EventStatus.DRAFT || status == EventStatus.UPCOMING
    }

    /**
     * Check if event is visible to public
     */
    fun isPubliclyVisible(): Boolean {
        return status == EventStatus.UPCOMING || status == EventStatus.PAST
    }
}

