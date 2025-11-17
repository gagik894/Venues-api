package app.venues.booking.domain

import app.venues.common.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * A "root" entity for a guest checkout.
 * Stores info for a non-authenticated booking.
 *
 * @param email The guest's email.
 * @param name The guest's full name.
 * @param phone The guest's phone number (optional).
 */
@Entity
@Table(
    name = "guests",
    indexes = [Index(name = "idx_guest_email", columnList = "email", unique = true)]
)
class Guest(
    @Column(nullable = false, unique = true, length = 255)
    var email: String,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(length = 20)
    var phone: String? = null,

    ) : AbstractUuidEntity()