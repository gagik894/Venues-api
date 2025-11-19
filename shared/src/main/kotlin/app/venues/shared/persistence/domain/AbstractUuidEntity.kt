package app.venues.shared.persistence.domain

import app.venues.common.util.IdGenerator
import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.*

/**
 * Abstract base entity providing UUID primary key and Spring Data auditing.
 *
 * This entity uses Spring Data JPA auditing for automatic timestamp management,
 * providing a reliable, framework-integrated solution for tracking entity lifecycle.
 *
 * Key Features:
 * - Time-ordered UUID (v7) for optimal database performance
 * - Automatic timestamp management via Spring Data auditing
 * - Proper equals/hashCode based on ID
 * - Thread-safe initialization
 *
 * Design Rationale:
 * UUIDs prevent primary key collisions across distributed systems while UUIDv7's
 * time-ordering prevents index fragmentation, combining the benefits of UUIDs
 * with the performance characteristics of sequential IDs. Spring Data auditing
 * ensures timestamps are set consistently by the framework.
 *
 * Requirements:
 * - Spring Data JPA auditing must be enabled via @EnableJpaAuditing
 * - See DatabaseConfig.kt for auditing configuration
 *
 * Use Cases:
 * - Distributed systems requiring globally unique IDs
 * - Entities that need external ID references
 * - Multi-tenant or sharded database architectures
 *
 * @property id Time-ordered UUID (v7) primary key
 * @property createdAt Timestamp when entity was first persisted (set by Spring Data)
 * @property lastModifiedAt Timestamp when entity was last updated (set by Spring Data)
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AbstractUuidEntity {

    @Id
    val id: UUID = IdGenerator.uuidv7()

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()

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

