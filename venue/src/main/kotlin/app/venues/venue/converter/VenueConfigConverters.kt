package app.venues.venue.converter

import app.venues.shared.security.VenueConfigEncryptionService
import app.venues.venue.dto.SmtpConfig
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component


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

