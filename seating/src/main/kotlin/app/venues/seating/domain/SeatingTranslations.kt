package app.venues.seating.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Translation for Level labels.
 *
 * Supports multi-language labels for sections/areas.
 */
@Entity
@Table(
    name = "level_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_level_translation_level_language",
            columnNames = ["level_id", "language"]
        )
    ],
    indexes = [
        Index(name = "idx_level_translation_level_id", columnList = "level_id"),
        Index(name = "idx_level_translation_language", columnList = "language")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class LevelTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The level this translation belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    var level: Level,

    /**
     * Language code (ISO 639-1: en, hy, ru, etc.)
     */
    @Column(nullable = false, length = 10)
    var language: String,

    /**
     * Translated level label
     */
    @Column(name = "level_label", nullable = false, length = 255)
    var levelLabel: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
)

/**
 * Translation for Seat labels.
 *
 * Supports multi-language labels for individual seats.
 */
@Entity
@Table(
    name = "seat_translations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_seat_translation_seat_language",
            columnNames = ["seat_id", "language"]
        )
    ],
    indexes = [
        Index(name = "idx_seat_translation_seat_id", columnList = "seat_id"),
        Index(name = "idx_seat_translation_language", columnList = "language")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class SeatTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The seat this translation belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    var seat: Seat,

    /**
     * Language code (ISO 639-1: en, hy, ru, etc.)
     */
    @Column(nullable = false, length = 10)
    var language: String,

    /**
     * Translated seat label
     */
    @Column(nullable = false, length = 255)
    var label: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
)

