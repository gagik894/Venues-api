package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Entity representing a translation of venue information in different languages.
 *
 * Supports multi-language venue names and descriptions for international accessibility.
 * This allows venues to provide information in multiple languages for diverse audiences.
 */
@Entity
@Table(
    name = "venue_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_venue_translation_venue_language",
            columnNames = ["venue_id", "language"]
        )
    ],
    indexes = [
        Index(name = "idx_venue_translation_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_translation_language", columnList = "language")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenueTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The venue this translation belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * Language code (e.g., "en", "fr", "es", "hy", "ru")
     */
    @Column(name = "language", nullable = false, length = 10)
    var language: String,

    /**
     * Translated venue name
     */
    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    /**
     * Translated venue description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    // ===========================================
    // Audit Fields
    // ===========================================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
)
