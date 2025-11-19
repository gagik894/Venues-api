package app.venues.venue.service

import app.venues.venue.domain.VenueSettings
import app.venues.venue.dto.PaymentConfig
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
     * Update payment configuration for a venue.
     * Encryption happens automatically via JPA AttributeConverter.
     */
    fun updatePaymentConfig(venueId: UUID, config: PaymentConfig) {
        val settings = getOrCreateSettings(venueId)
        settings.paymentConfig = config
        venueSettingsRepository.save(settings)

        logger.info { "Updated payment config for venue $venueId (providers: ${config.getConfiguredProviders()})" }
    }

    /**
     * Get decrypted payment configuration.
     * Decryption happens automatically via JPA AttributeConverter.
     * Returns null if not configured.
     */
    fun getPaymentConfig(venueId: UUID): PaymentConfig? {
        return venueSettingsRepository.findById(venueId)
            .map { it.paymentConfig }
            .orElse(null)
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
     * Check if venue has payment gateway configured.
     */
    fun hasPaymentConfig(venueId: UUID): Boolean {
        return venueSettingsRepository.findById(venueId)
            .map { it.hasPaymentConfig() }
            .orElse(false)
    }

    /**
     * Check if venue has SMTP configured.
     */
    fun hasSmtpConfig(venueId: UUID): Boolean {
        return venueSettingsRepository.findById(venueId)
            .map { it.hasSmtpConfig() }
            .orElse(false)
    }

    /**
     * Get masked payment config for safe logging/display.
     * Never log unmasked credentials!
     */
    fun getPaymentConfigMasked(venueId: UUID): PaymentConfig? {
        val config = getPaymentConfig(venueId) ?: return null

        return PaymentConfig(
            idram = config.idram?.toMasked(),
            telcel = config.telcel?.toMasked(),
            arca = config.arca?.toMasked(),
            converse = config.converse?.toMasked(),
            stripe = config.stripe?.toMasked()
        )
    }

    /**
     * Get masked SMTP config for safe logging/display.
     */
    fun getSmtpConfigMasked(venueId: UUID): SmtpConfig? {
        return getSmtpConfig(venueId)?.toMasked()
    }
}

