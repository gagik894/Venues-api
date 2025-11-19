package app.venues.venue.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Service for encrypting and decrypting sensitive venue configuration data.
 *
 * Uses AES-256-GCM for authenticated encryption with:
 * - Strong encryption (AES-256)
 * - Authentication (prevents tampering)
 * - Unique IV per encryption (prevents pattern analysis)
 *
 * Government-quality encryption suitable for storing:
 * - Payment gateway credentials
 * - SMTP passwords
 * - API keys and secrets
 */
@Service
class VenueConfigEncryptionService(
    @Value("\${venue.config.encryption.key:CHANGE_THIS_IN_PRODUCTION}")
    private val encryptionKey: String
) {

    private val algorithm = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12
    private val saltLength = 16
    private val keyLength = 256
    private val iterationCount = 65536

    /**
     * Encrypt JSON configuration data.
     *
     * @param plaintext The unencrypted JSON string
     * @return Base64-encoded encrypted data with IV and salt prepended
     * @throws IllegalArgumentException if encryption fails
     */
    fun encrypt(plaintext: String?): String? {
        if (plaintext.isNullOrBlank()) return null

        try {
            // Generate random salt and IV
            val salt = ByteArray(saltLength)
            val iv = ByteArray(ivLength)
            SecureRandom().apply {
                nextBytes(salt)
                nextBytes(iv)
            }

            // Derive key from password + salt
            val secretKey = deriveKey(encryptionKey, salt)

            // Encrypt
            val cipher = Cipher.getInstance(algorithm)
            val gcmSpec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Combine: salt + IV + ciphertext
            val combined = salt + iv + encrypted

            // Return as Base64
            return Base64.getEncoder().encodeToString(combined)

        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to encrypt configuration", e)
        }
    }

    /**
     * Decrypt JSON configuration data.
     *
     * @param ciphertext Base64-encoded encrypted data
     * @return Decrypted JSON string
     * @throws IllegalArgumentException if decryption fails (wrong key or corrupted data)
     */
    fun decrypt(ciphertext: String?): String? {
        if (ciphertext.isNullOrBlank()) return null

        try {
            // Decode from Base64
            val combined = Base64.getDecoder().decode(ciphertext)

            // Extract salt, IV, and encrypted data
            val salt = combined.copyOfRange(0, saltLength)
            val iv = combined.copyOfRange(saltLength, saltLength + ivLength)
            val encrypted = combined.copyOfRange(saltLength + ivLength, combined.size)

            // Derive key from password + salt
            val secretKey = deriveKey(encryptionKey, salt)

            // Decrypt
            val cipher = Cipher.getInstance(algorithm)
            val gcmSpec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decrypted = cipher.doFinal(encrypted)

            return String(decrypted, Charsets.UTF_8)

        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decrypt configuration (wrong key or corrupted data)", e)
        }
    }

    /**
     * Derive AES key from password using PBKDF2.
     * Uses salt to prevent rainbow table attacks.
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    /**
     * Check if data is encrypted (starts with valid Base64).
     */
    fun isEncrypted(data: String?): Boolean {
        if (data.isNullOrBlank()) return false
        return try {
            Base64.getDecoder().decode(data)
            true
        } catch (e: Exception) {
            false
        }
    }
}

