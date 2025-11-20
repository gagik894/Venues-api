package app.venues.venue.service

import app.venues.venue.domain.VenueSettings
import app.venues.venue.dto.SmtpConfig
import app.venues.venue.repository.VenueRepository
import app.venues.venue.repository.VenueSettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for managing venue settings with automatic encryption.
 *
 * Handles:
 * - Payment gateway configuration
 * - SMTP configuration
 * - Safe retrieval without manual encryption/decryption
 *
 * All sensitive data is automatically encrypted at rest using JPA AttributeConverters.
 * No manual encryption/decryption needed - work with plain objects!
 */
@Service
@Transactional
class VenueSettingsService(
    private val venueSettingsRepository: VenueSettingsRepository,
    private val venueRepository: VenueRepository
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Get or create settings for a venue.
     * Automatically creates settings record if it doesn't exist.
     */
    fun getOrCreateSettings(venueId: UUID): VenueSettings {
        return venueSettingsRepository.findById(venueId).orElseGet {
            val venue = venueRepository.findById(venueId).orElseThrow {
                IllegalArgumentException("Venue not found: $venueId")
            }

            val settings = VenueSettings(venue = venue)
            settings.id = venueId
            venueSettingsRepository.save(settings)
        }
    }


    /**
     * Update SMTP configuration for a venue.
     * Encryption happens automatically via JPA AttributeConverter.
     */
    fun updateSmtpConfig(venueId: UUID, config: SmtpConfig) {
        val settings = getOrCreateSettings(venueId)
        settings.smtpConfig = config
        venueSettingsRepository.save(settings)

        logger.info { "Updated SMTP config for venue $venueId (email: ${config.email})" }
    }

    /**
     * Get decrypted SMTP configuration.
     * Decryption happens automatically via JPA AttributeConverter.
     * Returns null if not configured.
     */
    fun getSmtpConfig(venueId: UUID): SmtpConfig? {
        return venueSettingsRepository.findById(venueId)
            .map { it.smtpConfig }
            .orElse(null)
    }

    /**
     * Delete all settings for a venue.
     * Use when venue is being deleted.
     */
    fun deleteSettings(venueId: UUID) {
        venueSettingsRepository.deleteById(venueId)
        logger.info { "Deleted settings for venue $venueId" }
    }


    /**
     * Get masked SMTP config for safe logging/display.
     */
    fun getSmtpConfigMasked(venueId: UUID): SmtpConfig? {
        return getSmtpConfig(venueId)?.toMasked()
    }
}

