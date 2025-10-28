package app.venues.booking.domain

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Guest entity for unauthenticated bookings.
 *
 * Guests are identified by email and stored separately for reuse across multiple bookings.
 * This allows guests to view their booking history without creating an account.
 */
@Entity
@Table(
    name = "guests",
    indexes = [
        Index(name = "idx_guest_email", columnList = "email")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Guest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * Guest email (unique identifier)
     */
    @Column(nullable = false, unique = true, length = 255)
    var email: String,

    /**
     * Guest full name
     */
    @Column(nullable = false, length = 200)
    var name: String,

    /**
     * Guest phone number (optional)
     */
    @Column(length = 20)
    var phone: String? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    var lastModifiedAt: Instant = Instant.now()
)

