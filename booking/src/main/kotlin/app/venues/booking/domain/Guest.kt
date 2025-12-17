package app.venues.booking.domain

import app.venues.shared.persistence.domain.AbstractUuidEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * Represents a guest user for non-authenticated bookings.
 *
 * @param email The guest's email address.
 * @param name The guest's full name.
 * @param phone The guest's phone number (optional).
 */
@Entity
@Table(
    name = "guests",
    indexes = [Index(name = "idx_guest_email", columnList = "email", unique = true)]
)
class Guest(
    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String,

    @Column(name = "name", nullable = false, length = 200)
    var name: String,

    @Column(name = "phone", length = 20)
    var phone: String? = null,

    /**
     * The guest's preferred language for email communications.
     * Supports: 'en' (English), 'hy' (Armenian), 'ru' (Russian)
     */
    @Column(name = "preferred_language", length = 5, nullable = false)
    var preferredLanguage: String = "en"

    ) : AbstractUuidEntity()
