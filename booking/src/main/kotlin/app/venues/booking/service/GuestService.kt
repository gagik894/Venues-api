package app.venues.booking.service

import app.venues.booking.domain.Guest
import app.venues.booking.repository.GuestRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for guest management.
 *
 * Handles guest creation and retrieval for unauthenticated bookings.
 */
@Service
@Transactional
class GuestService(
    private val guestRepository: GuestRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Find guest by email or create new one.
     */
    fun findOrCreateGuest(email: String, name: String, phone: String?): Guest {
        val normalizedEmail = email.lowercase().trim()

        return guestRepository.findByEmailIgnoreCase(normalizedEmail)?.also {
            // Update guest info if needed
            var updated = false
            if (it.name != name) {
                it.name = name
                updated = true
            }
            if (phone != null && it.phone != phone) {
                it.phone = phone
                updated = true
            }
            if (updated) {
                guestRepository.save(it)
                logger.debug { "Updated guest info: $normalizedEmail" }
            }
        } ?: run {
            // Create new guest
            val newGuest = Guest(
                email = normalizedEmail,
                name = name,
                phone = phone
            )
            guestRepository.save(newGuest).also {
                logger.info { "Created new guest: $normalizedEmail" }
            }
        }
    }

    /**
     * Find guest by email.
     */
    @Transactional(readOnly = true)
    fun findGuestByEmail(email: String): Guest? {
        return guestRepository.findByEmailIgnoreCase(email.lowercase().trim())
    }
}

