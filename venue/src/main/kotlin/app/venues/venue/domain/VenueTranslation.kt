package app.venues.venue.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Venue Translation Entity
 *
 * Provides multi-language support for venue information.
 * Each venue can have translations in multiple languages.
 *
 * Translatable fields:
 * - name
 * - description
 *
 * Language codes follow ISO 639-1 standard (e.g., "en", "es", "fr", "hy")
 */
@Entity
@Table(
    name = "venue_translations",
    indexes = [
        Index(name = "idx_venue_translation_venue_id", columnList = "venue_id"),
        Index(name = "idx_venue_translation_language", columnList = "language")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_venue_translation_venue_language", columnNames = ["venue_id", "language"])
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class VenueTranslation(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    var venue: Venue,

    /**
     * ISO 639-1 language code (e.g., "en", "es", "fr", "hy", "ru")
     */
    @Column(nullable = false, length = 10)
    var language: String,

    /**
     * Translated venue name
     */
    @Column(nullable = false, length = 255)
    var name: String,

    /**
     * Translated venue description
     */
    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VenueTranslation

        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "VenueTranslation(id=$id, language='$language', name='$name')"
    }
}

