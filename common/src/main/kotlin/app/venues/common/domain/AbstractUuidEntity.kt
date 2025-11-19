package app.venues.common.domain

import app.venues.common.util.IdGenerator
import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Abstract base entity providing UUID primary key and audit timestamp management.
 *
 * This entity uses pure JPA lifecycle callbacks for timestamp management, ensuring
 * it can be used in any JPA-compliant environment without Spring dependencies.
 *
 * Key Features:
 * - Time-ordered UUID (v7) for optimal database performance
 * - Automatic timestamp management via JPA callbacks
 * - Proper equals/hashCode based on ID
 * - Thread-safe initialization
 *
 * Design Rationale:
 * UUIDs prevent primary key collisions across distributed systems while UUIDv7's
 * time-ordering prevents index fragmentation, combining the benefits of UUIDs
 * with the performance characteristics of sequential IDs.
 *
 * @property id Time-ordered UUID (v7) primary key
 * @property createdAt Timestamp when entity was first persisted
 * @property lastModifiedAt Timestamp when entity was last updated
 */
@MappedSuperclass
open class AbstractUuidEntity {
    @Id
    open val id: UUID = IdGenerator.uuidv7()

    @Column(name = "created_at", nullable = false, updatable = false)
    open var createdAt: Instant = Instant.now()

    @Column(name = "last_modified_at", nullable = false)
    open var lastModifiedAt: Instant = Instant.now()

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
        other as AbstractUuidEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "${this.javaClass.simpleName}(id=$id)"
    }
}