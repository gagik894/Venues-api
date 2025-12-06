package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.venue.domain.VenueSettings
import app.venues.venue.dto.SmtpConfig
import app.venues.venue.repository.VenueRepository
import app.venues.venue.repository.VenueSettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
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
    private val venueRepository: VenueRepository,
    private val entityManager: EntityManager
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Get or create settings for a venue.
     * Automatically creates settings record if it doesn't exist.
     * Note: Only use for read operations. For updates, use updateSmtpConfig directly.
     */
    fun getOrCreateSettings(venueId: UUID): VenueSettings {
        return venueSettingsRepository.findById(venueId).orElseGet {
            val venue = venueRepository.findById(venueId).orElseThrow {
                VenuesException.ResourceNotFound("Venue not found: $venueId", "VENUE_NOT_FOUND")
            }

            val settings = VenueSettings(venue = venue)
            settings.id = venueId
            entityManager.persist(settings)
            settings
        }
    }


    /**
     * Update SMTP configuration for a venue.
     * Encryption happens automatically via JPA AttributeConverter.
     * Pass null to remove the configuration.
     */
    fun updateSmtpConfig(venueId: UUID, config: SmtpConfig?) {
        // Check if settings exist
        val existing = venueSettingsRepository.findById(venueId).orElse(null)

        val settings = if (existing != null) {
            // Update existing - it's already managed
            existing.smtpConfig = config
            existing
        } else {
            // Create new
            val venue = venueRepository.findById(venueId).orElseThrow {
                VenuesException.ResourceNotFound("Venue not found: $venueId", "VENUE_NOT_FOUND")
            }
            val newSettings = VenueSettings(venue = venue)
            newSettings.id = venueId
            newSettings.smtpConfig = config
            entityManager.persist(newSettings)
            newSettings
        }

        if (config != null) {
            logger.info { "Updated SMTP config for venue $venueId (email: ${config.email})" }
        } else {
            logger.info { "Deleted SMTP config for venue $venueId" }
        }
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

