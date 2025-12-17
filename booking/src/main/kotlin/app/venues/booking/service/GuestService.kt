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

    companion object {
        private const val NO_EMAIL_DOMAIN = "noemail.booking.invalid"

        fun isPlaceholderEmail(email: String?): Boolean {
            return email?.lowercase()?.endsWith("@${NO_EMAIL_DOMAIN}") == true
        }
    }

    /**
     * Find guest by email or create new one.
     *
     * @param email Guest's email address (nullable for walk-up sales)
     * @param name Guest's name
     * @param phone Guest's phone number (optional)
     * @param preferredLanguage Guest's preferred language from Accept-Language header
     */
    fun findOrCreateGuest(email: String?, name: String, phone: String?, preferredLanguage: String = "en"): Guest {
        val normalizedEmail = normalizeEmail(email)

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
            // Update language preference to the latest one used
            if (it.preferredLanguage != preferredLanguage) {
                it.preferredLanguage = preferredLanguage
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
                phone = phone,
                preferredLanguage = preferredLanguage
            )
            guestRepository.save(newGuest).also {
                logger.info { "Created new guest: $normalizedEmail with language: $preferredLanguage" }
            }
        }
    }

    fun normalizeEmail(email: String?): String {
        val trimmed = email?.trim()
        if (trimmed.isNullOrBlank()) {
            return "guest-${java.util.UUID.randomUUID()}@${NO_EMAIL_DOMAIN}"
        }
        return trimmed.lowercase()
    }

    /**
     * Find guest by email.
     */
    @Transactional(readOnly = true)
    fun findGuestByEmail(email: String): Guest? {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return null
        return guestRepository.findByEmailIgnoreCase(trimmed.lowercase())
    }
}

