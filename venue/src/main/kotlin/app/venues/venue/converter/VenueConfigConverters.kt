package app.venues.venue.converter

import app.venues.venue.dto.PaymentConfig
import app.venues.venue.dto.SmtpConfig
import app.venues.venue.service.VenueConfigEncryptionService
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

/**
 * JPA AttributeConverter for PaymentConfig.
 * Automatically encrypts on save and decrypts on load.
 *
 * Benefits:
 * - Transparent encryption (business logic works with plain objects)
 * - No manual encryption/decryption in service layer
 * - Type-safe (PaymentConfig object, not String)
 * - Automatic with JPA lifecycle
 */
@Converter
@Component
class PaymentConfigConverter(
    private val encryptionService: VenueConfigEncryptionService,
    private val objectMapper: ObjectMapper
) : AttributeConverter<PaymentConfig?, String?> {

    private val logger = KotlinLogging.logger {}

    /**
     * Convert PaymentConfig object to encrypted database column.
     * Called automatically when saving/updating entity.
     */
    override fun convertToDatabaseColumn(attribute: PaymentConfig?): String? {
        if (attribute == null) return null

        return try {
            // Serialize to JSON
            val json = objectMapper.writeValueAsString(attribute)

            // Encrypt
            val encrypted = encryptionService.encrypt(json)

            logger.debug { "Encrypted payment config for storage" }
            encrypted

        } catch (e: Exception) {
            logger.error(e) { "Failed to encrypt payment config" }
            throw IllegalStateException("Failed to encrypt payment configuration", e)
        }
    }

    /**
     * Convert encrypted database column to PaymentConfig object.
     * Called automatically when loading entity.
     */
    override fun convertToEntityAttribute(dbData: String?): PaymentConfig? {
        if (dbData.isNullOrBlank()) return null

        return try {
            // Decrypt
            val json = encryptionService.decrypt(dbData)

            // Deserialize from JSON
            val config = objectMapper.readValue(json, PaymentConfig::class.java)

            logger.debug { "Decrypted payment config from storage" }
            config

        } catch (e: Exception) {
            logger.error(e) { "Failed to decrypt payment config (wrong key or corrupted data)" }
            // Return null instead of throwing to prevent app crash
            // Log the error for investigation
            null
        }
    }
}

/**
 * JPA AttributeConverter for SmtpConfig.
 * Automatically encrypts on save and decrypts on load.
 */
@Converter
@Component
class SmtpConfigConverter(
    private val encryptionService: VenueConfigEncryptionService,
    private val objectMapper: ObjectMapper
) : AttributeConverter<SmtpConfig?, String?> {

    private val logger = KotlinLogging.logger {}

    override fun convertToDatabaseColumn(attribute: SmtpConfig?): String? {
        if (attribute == null) return null

        return try {
            val json = objectMapper.writeValueAsString(attribute)
            val encrypted = encryptionService.encrypt(json)
            logger.debug { "Encrypted SMTP config for storage" }
            encrypted
        } catch (e: Exception) {
            logger.error(e) { "Failed to encrypt SMTP config" }
            throw IllegalStateException("Failed to encrypt SMTP configuration", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): SmtpConfig? {
        if (dbData.isNullOrBlank()) return null

        return try {
            val json = encryptionService.decrypt(dbData)
            val config = objectMapper.readValue(json, SmtpConfig::class.java)
            logger.debug { "Decrypted SMTP config from storage" }
            config
        } catch (e: Exception) {
            logger.error(e) { "Failed to decrypt SMTP config (wrong key or corrupted data)" }
            null
        }
    }
}

