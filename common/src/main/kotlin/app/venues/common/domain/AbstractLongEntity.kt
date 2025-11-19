package app.venues.common.domain

import jakarta.persistence.*
import java.time.Instant

/**
 * Abstract base entity providing auto-increment Long primary key and audit timestamp management.
 *
 * This entity uses pure JPA lifecycle callbacks for timestamp management, ensuring
 * it can be used in any JPA-compliant environment without Spring dependencies.
 *
 * Key Features:
 * - Database-generated sequential Long ID (IDENTITY strategy)
 * - Automatic timestamp management via JPA callbacks
 * - Proper equals/hashCode based on ID with null-safety
 * - Optimized for high-volume insertions
 *
 * Design Rationale:
 * Long IDs offer optimal performance for single-database scenarios with high insertion
 * rates. The IDENTITY strategy leverages database sequences for thread-safe ID generation.
 *
 * Use Cases:
 * - High-volume transactional data (bookings, cart items)
 * - Entities with intensive INSERT operations
 * - Single-database deployments without distributed requirements
 *
 * @property id Database-generated sequential Long primary key (null until persisted)
 * @property createdAt Timestamp when entity was first persisted
 * @property lastModifiedAt Timestamp when entity was last updated
 */
@MappedSuperclass
class AbstractLongEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()

    /**
     * JPA callback executed before entity is persisted to database.
     * Initializes both timestamps to current time.
     */
    @PrePersist
    protected fun onInsert() {
        val now = Instant.now()
        createdAt = now
        lastModifiedAt = now
    }

    /**
     * JPA callback executed before entity updates are flushed to database.
     * Updates the lastModifiedAt timestamp to current time.
     */
    @PreUpdate
    protected fun onUpdate() {
        lastModifiedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AbstractLongEntity
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String {
        return "${this.javaClass.simpleName}(id=$id)"
    }
}