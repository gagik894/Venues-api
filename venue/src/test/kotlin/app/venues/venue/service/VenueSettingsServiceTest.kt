package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.location.domain.City
import app.venues.location.domain.Region
import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenueSettings
import app.venues.venue.dto.SmtpConfig
import app.venues.venue.repository.VenueRepository
import app.venues.venue.repository.VenueSettingsRepository
import io.mockk.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class VenueSettingsServiceTest {

    private val venueSettingsRepository: VenueSettingsRepository = mockk()
    private val venueRepository: VenueRepository = mockk()
    private val entityManager: EntityManager = mockk(relaxed = true)

    private lateinit var service: VenueSettingsService

    private val venueId = UUID.randomUUID()
    private lateinit var testVenue: Venue

    @BeforeEach
    fun setup() {
        service = VenueSettingsService(
            venueSettingsRepository,
            venueRepository,
            entityManager
        )

        val region = Region(code = "AM-ER", names = mapOf("en" to "Yerevan"))
        val city = City(region = region, slug = "yerevan", names = mapOf("en" to "Yerevan"))
        testVenue = Venue(
            name = "Test Venue",
            description = "A test venue",
            slug = "test-venue",
            organizationId = UUID.randomUUID(),
            address = "123 Test St",
            city = city
        )
    }

    @Nested
    inner class GetOrCreateSettings {

        @Test
        fun `returns existing settings when found`() {
            val existingSettings = VenueSettings(venue = testVenue).apply {
                id = venueId
                smtpConfig = SmtpConfig(
                    email = "test@example.com",
                    password = "secret",
                    host = "smtp.example.com",
                    port = 587
                )
            }

            every { venueSettingsRepository.findById(venueId) } returns Optional.of(existingSettings)

            val result = service.getOrCreateSettings(venueId)

            assertEquals(existingSettings, result)
            verify(exactly = 0) { entityManager.persist(any()) }
        }

        @Test
        fun `creates new settings when not found`() {
            every { venueSettingsRepository.findById(venueId) } returns Optional.empty()
            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)

            val result = service.getOrCreateSettings(venueId)

            assertNotNull(result)
            assertEquals(testVenue, result.venue)
            verify { entityManager.persist(any()) }
        }

        @Test
        fun `throws ResourceNotFound when venue not found`() {
            every { venueSettingsRepository.findById(venueId) } returns Optional.empty()
            every { venueRepository.findById(venueId) } returns Optional.empty()

            val exception = assertThrows<VenuesException.ResourceNotFound> {
                service.getOrCreateSettings(venueId)
            }

            assertTrue(exception.message!!.contains("Venue not found"))
        }
    }

    @Nested
    inner class UpdateSmtpConfig {

        @Test
        fun `updates existing settings with smtp config`() {
            val existingSettings = VenueSettings(venue = testVenue).apply {
                id = venueId
            }
            val newConfig = SmtpConfig(
                email = "new@example.com",
                password = "newpassword",
                host = "smtp.new.com",
                port = 465
            )

            every { venueSettingsRepository.findById(venueId) } returns Optional.of(existingSettings)

            service.updateSmtpConfig(venueId, newConfig)

            assertEquals(newConfig, existingSettings.smtpConfig)
            verify(exactly = 0) { entityManager.persist(any()) }
        }

        @Test
        fun `creates new settings with smtp config when none exists`() {
            val newConfig = SmtpConfig(
                email = "new@example.com",
                password = "newpassword",
                host = "smtp.new.com",
                port = 465
            )

            every { venueSettingsRepository.findById(venueId) } returns Optional.empty()
            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)

            service.updateSmtpConfig(venueId, newConfig)

            verify { entityManager.persist(match<VenueSettings> { it.smtpConfig == newConfig }) }
        }

        @Test
        fun `clears smtp config when null is passed`() {
            val existingSettings = VenueSettings(venue = testVenue).apply {
                id = venueId
                smtpConfig = SmtpConfig(
                    email = "old@example.com",
                    password = "oldpassword",
                    host = "smtp.old.com",
                    port = 587
                )
            }

            every { venueSettingsRepository.findById(venueId) } returns Optional.of(existingSettings)

            service.updateSmtpConfig(venueId, null)

            assertNull(existingSettings.smtpConfig)
        }

        @Test
        fun `throws ResourceNotFound when venue not found for new settings`() {
            val config = SmtpConfig(
                email = "test@example.com",
                password = "secret",
                host = "smtp.example.com",
                port = 587
            )

            every { venueSettingsRepository.findById(venueId) } returns Optional.empty()
            every { venueRepository.findById(venueId) } returns Optional.empty()

            assertThrows<VenuesException.ResourceNotFound> {
                service.updateSmtpConfig(venueId, config)
            }
        }
    }

    @Nested
    inner class GetSmtpConfig {

        @Test
        fun `returns smtp config when exists`() {
            val expectedConfig = SmtpConfig(
                email = "test@example.com",
                password = "secret",
                host = "smtp.example.com",
                port = 587
            )
            val settings = VenueSettings(venue = testVenue).apply {
                id = venueId
                smtpConfig = expectedConfig
            }

            every { venueSettingsRepository.findById(venueId) } returns Optional.of(settings)

            val result = service.getSmtpConfig(venueId)

            assertEquals(expectedConfig, result)
        }

        @Test
        fun `returns null when settings not found`() {
            every { venueSettingsRepository.findById(venueId) } returns Optional.empty()

            val result = service.getSmtpConfig(venueId)

            assertNull(result)
        }

        @Test
        fun `returns null when smtp config not set`() {
            val settings = VenueSettings(venue = testVenue).apply {
                id = venueId
                smtpConfig = null
            }

            every { venueSettingsRepository.findById(venueId) } returns Optional.of(settings)

            val result = service.getSmtpConfig(venueId)

            assertNull(result)
        }
    }

    @Nested
    inner class GetSmtpConfigMasked {

        @Test
        fun `returns masked smtp config`() {
            val config = SmtpConfig(
                email = "test@example.com",
                password = "supersecretpassword",
                host = "smtp.example.com",
                port = 587
            )
            val settings = VenueSettings(venue = testVenue).apply {
                id = venueId
                smtpConfig = config
            }

            every { venueSettingsRepository.findById(venueId) } returns Optional.of(settings)

            val result = service.getSmtpConfigMasked(venueId)

            assertNotNull(result)
            assertEquals("test@example.com", result!!.email)
            assertEquals("smtp.example.com", result.host)
            // Password should be masked
            assertNotEquals("supersecretpassword", result.password)
        }

        @Test
        fun `returns null when no config`() {
            every { venueSettingsRepository.findById(venueId) } returns Optional.empty()

            val result = service.getSmtpConfigMasked(venueId)

            assertNull(result)
        }
    }

    @Nested
    inner class DeleteSettings {

        @Test
        fun `deletes settings by venue id`() {
            every { venueSettingsRepository.deleteById(venueId) } just runs

            service.deleteSettings(venueId)

            verify { venueSettingsRepository.deleteById(venueId) }
        }
    }
}
