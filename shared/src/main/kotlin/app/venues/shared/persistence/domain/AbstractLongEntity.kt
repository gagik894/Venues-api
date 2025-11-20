package app.venues.shared.persistence.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Abstract base entity providing auto-increment Long primary key and Spring Data auditing.
 *
 * This entity uses Spring Data JPA auditing for automatic timestamp management,
 * providing a reliable, framework-integrated solution for tracking entity lifecycle.
 *
 * Key Features:
 * - Database-generated sequential Long ID (IDENTITY strategy)
 * - Automatic timestamp management via Spring Data auditing
 * - Proper equals/hashCode based on ID with null-safety
 * - Optimized for high-volume insertions
 *
 * Design Rationale:
 * Long IDs offer optimal performance for single-database scenarios with high insertion
 * rates. The IDENTITY strategy leverages database sequences for thread-safe ID generation.
 * Spring Data auditing ensures timestamps are set consistently by the framework.
 *
 * Requirements:
 * - Spring Data JPA auditing must be enabled via @EnableJpaAuditing
 * - See DatabaseConfig.kt for auditing configuration
 *
 * Use Cases:
 * - High-volume transactional data (bookings, cart items)
 * - Entities with intensive INSERT operations
 * - Single-database deployments without distributed requirements
 *
 * @property id Database-generated sequential Long primary key (null until persisted)
 * @property createdAt Timestamp when entity was first persisted (set by Spring Data)
 * @property lastModifiedAt Timestamp when entity was last updated (set by Spring Data)
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AbstractLongEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()

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

