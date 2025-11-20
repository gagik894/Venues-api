package app.venues.shared.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VenueConfigEncryptionServiceTest {

    private val service = VenueConfigEncryptionService("test-encryption-key-must-be-long-enough")

    @Test
    fun `should encrypt and decrypt successfully`() {
        val original = "{\"secret\":\"value\"}"
        val encrypted = service.encrypt(original)

        assertNotNull(encrypted)
        assertNotEquals(original, encrypted)
        assertTrue(service.isEncrypted(encrypted))

        val decrypted = service.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `should return null for null input`() {
        assertNull(service.encrypt(null))
        assertNull(service.decrypt(null))
    }

    @Test
    fun `should throw exception for invalid key`() {
        val original = "test"
        val encrypted = service.encrypt(original)

        // Create service with different key
        val otherService = VenueConfigEncryptionService("wrong-key-wrong-key-wrong-key-wrong")

        assertThrows<IllegalArgumentException> {
            otherService.decrypt(encrypted)
        }
    }
}
