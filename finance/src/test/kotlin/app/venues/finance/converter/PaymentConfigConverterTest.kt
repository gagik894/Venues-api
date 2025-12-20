package app.venues.finance.converter

import app.venues.finance.api.dto.IdramConfig
import app.venues.finance.api.dto.PaymentConfig
import app.venues.shared.security.VenueConfigEncryptionService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentConfigConverterTest {

    private val encryptionService = mockk<VenueConfigEncryptionService>()
    private val objectMapper = jacksonObjectMapper() // Use real mapper
    private val converter = PaymentConfigConverter(encryptionService, objectMapper)

    @Test
    fun `should encrypt data on save`() {
        val config = PaymentConfig(idram = IdramConfig("123", "secret"))
        val json = objectMapper.writeValueAsString(config)

        every { encryptionService.encrypt(any()) } returns "encrypted-data"

        val result = converter.convertToDatabaseColumn(config)

        assertEquals("encrypted-data", result)
    }

    @Test
    fun `should decrypt data on load`() {
        val config = PaymentConfig(idram = IdramConfig("123", "secret"))
        val json = objectMapper.writeValueAsString(config)

        every { encryptionService.decrypt("encrypted-data") } returns json

        val result = converter.convertToEntityAttribute("encrypted-data")

        assertNotNull(result)
        assertEquals("123", result?.idram?.recAccount)
        assertEquals("secret", result?.idram?.secretKey)
    }

    @Test
    fun `should throw exception on decryption failure`() {
        every { encryptionService.decrypt("corrupted-data") } throws RuntimeException("Bad key")

        val exception = assertThrows<IllegalStateException> {
            converter.convertToEntityAttribute("corrupted-data")
        }

        assertTrue(exception.message!!.contains("Fatal"))
        assertTrue(exception.message!!.contains("Failed to decrypt"))
    }

    @Test
    fun `should handle nulls`() {
        assertNull(converter.convertToDatabaseColumn(null))
        assertNull(converter.convertToEntityAttribute(null))
        assertNull(converter.convertToEntityAttribute(""))
    }
}
