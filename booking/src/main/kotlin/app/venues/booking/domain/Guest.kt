package app.venues.booking.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

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
class Guest(
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
) : AbstractUuidEntity()

