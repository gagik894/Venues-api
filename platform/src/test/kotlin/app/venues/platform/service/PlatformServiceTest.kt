package app.venues.platform.service

import app.venues.common.exception.VenuesException
import app.venues.platform.api.dto.CreatePlatformRequest
import app.venues.platform.api.dto.RegenerateSecretRequest
import app.venues.platform.api.dto.UpdatePlatformRequest
import app.venues.platform.domain.Platform
import app.venues.platform.domain.PlatformStatus
import app.venues.platform.repository.PlatformRepository
import app.venues.platform.repository.WebhookEventRepository
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.*

/**
 * Unit tests for PlatformService (admin CRUD operations).
 */
class PlatformServiceTest {

    @MockK
    private lateinit var platformRepository: PlatformRepository

    @MockK
    private lateinit var webhookEventRepository: WebhookEventRepository

    private lateinit var service: PlatformService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        clearMocks(platformRepository, webhookEventRepository)
        service = PlatformService(platformRepository, webhookEventRepository)
    }

    // ===========================================
    // CREATE PLATFORM TESTS
    // ===========================================

    @Test
    fun `createPlatform succeeds with valid request`() {
        val request = CreatePlatformRequest(
            name = "TestPlatform",
            apiUrl = "https://api.test.com",
            description = "Test description",
            contactEmail = "test@test.com",
            rateLimit = 100
        )

        every { platformRepository.existsByName("TestPlatform") } returns false
        every { platformRepository.save(any()) } answers {
            val platform = firstArg<Platform>()
            platform
        }

        val result = service.createPlatform(request)

        assertEquals("TestPlatform", result.name)
        assertEquals("https://api.test.com", result.apiUrl)
        assertEquals(PlatformStatus.ACTIVE, result.status)
        assertTrue(result.sharedSecret.isNotBlank())
        assertEquals(128, result.sharedSecret.length) // 64 bytes * 2 hex chars

        verify(exactly = 1) { platformRepository.existsByName("TestPlatform") }
        verify(exactly = 1) { platformRepository.save(any()) }
    }

    @Test
    fun `createPlatform throws ResourceConflict when name exists`() {
        val request = CreatePlatformRequest(
            name = "ExistingPlatform",
            apiUrl = "https://api.test.com"
        )

        every { platformRepository.existsByName("ExistingPlatform") } returns true

        val exception = assertThrows<VenuesException.ResourceConflict> {
            service.createPlatform(request)
        }

        assertTrue(exception.message?.contains("already exists") == true)
        verify(exactly = 0) { platformRepository.save(any()) }
    }

    @Test
    fun `createPlatform generates unique shared secrets`() {
        val request = CreatePlatformRequest(
            name = "Platform1",
            apiUrl = "https://api.test.com"
        )

        val secrets = mutableSetOf<String>()

        every { platformRepository.existsByName(any()) } returns false
        every { platformRepository.save(any()) } answers {
            val platform = firstArg<Platform>()
            secrets.add(platform.sharedSecret)
            platform
        }

        // Create multiple platforms
        repeat(5) { i ->
            service.createPlatform(request.copy(name = "Platform$i"))
        }

        // All secrets should be unique
        assertEquals(5, secrets.size)
    }

    // ===========================================
    // UPDATE PLATFORM TESTS
    // ===========================================

    @Test
    fun `updatePlatform succeeds with valid request`() {
        val platformId = UUID.randomUUID()
        val platform = Platform(
            name = "TestPlatform",
            apiUrl = "https://old.api.com",
            sharedSecret = "secret"
        )

        val request = UpdatePlatformRequest(
            apiUrl = "https://new.api.com",
            description = "Updated description",
            contactEmail = "new@test.com",
            rateLimit = 200
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { platformRepository.save(any()) } answers { firstArg() }

        val result = service.updatePlatform(platformId, request)

        assertEquals("https://new.api.com", result.apiUrl)
        assertEquals("Updated description", result.description)
        assertEquals("new@test.com", result.contactEmail)
        assertEquals(200, result.rateLimit)
    }

    @Test
    fun `updatePlatform throws ResourceNotFound for non-existent platform`() {
        val platformId = UUID.randomUUID()
        val request = UpdatePlatformRequest(apiUrl = "https://new.api.com")

        every { platformRepository.findById(platformId) } returns Optional.empty()

        assertThrows<VenuesException.ResourceNotFound> {
            service.updatePlatform(platformId, request)
        }
    }

    // ===========================================
    // REGENERATE SECRET TESTS
    // ===========================================

    @Test
    fun `regenerateSharedSecret succeeds with confirmation`() {
        val platformId = UUID.randomUUID()
        val oldSecret = "old-secret-value"
        val platform = Platform(
            name = "TestPlatform",
            apiUrl = "https://api.test.com",
            sharedSecret = oldSecret
        )

        val request = RegenerateSecretRequest(confirm = true)

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { platformRepository.save(any()) } answers { firstArg() }

        val result = service.regenerateSharedSecret(platformId, request)

        assertNotEquals(oldSecret, result.sharedSecret)
        assertEquals(128, result.sharedSecret.length) // 64 bytes * 2 hex chars
    }

    @Test
    fun `regenerateSharedSecret throws ValidationFailure without confirmation`() {
        val platformId = UUID.randomUUID()
        val request = RegenerateSecretRequest(confirm = false)

        val exception = assertThrows<VenuesException.ValidationFailure> {
            service.regenerateSharedSecret(platformId, request)
        }

        assertTrue(exception.message?.contains("Confirmation required") == true)
        verify(exactly = 0) { platformRepository.findById(any()) }
    }

    @Test
    fun `regenerateSharedSecret throws ResourceNotFound for non-existent platform`() {
        val platformId = UUID.randomUUID()
        val request = RegenerateSecretRequest(confirm = true)

        every { platformRepository.findById(platformId) } returns Optional.empty()

        assertThrows<VenuesException.ResourceNotFound> {
            service.regenerateSharedSecret(platformId, request)
        }
    }

    // ===========================================
    // GET PLATFORM TESTS
    // ===========================================

    @Test
    fun `getPlatformById returns platform response`() {
        val platformId = UUID.randomUUID()
        val platform = Platform(
            name = "TestPlatform",
            apiUrl = "https://api.test.com",
            sharedSecret = "secret"
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)

        val result = service.getPlatformById(platformId)

        assertEquals("TestPlatform", result.name)
        assertEquals("https://api.test.com", result.apiUrl)
    }

    @Test
    fun `getPlatformById throws ResourceNotFound for non-existent platform`() {
        val platformId = UUID.randomUUID()

        every { platformRepository.findById(platformId) } returns Optional.empty()

        assertThrows<VenuesException.ResourceNotFound> {
            service.getPlatformById(platformId)
        }
    }

    @Test
    fun `getAllPlatforms returns paginated results`() {
        val platforms = listOf(
            Platform(name = "Platform1", apiUrl = "https://api1.test.com", sharedSecret = "s1"),
            Platform(name = "Platform2", apiUrl = "https://api2.test.com", sharedSecret = "s2")
        )
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl(platforms, pageable, 2)

        every { platformRepository.findAll(pageable) } returns page

        val result = service.getAllPlatforms(pageable)

        assertEquals(2, result.totalElements)
        assertEquals("Platform1", result.content[0].name)
        assertEquals("Platform2", result.content[1].name)
    }

    // ===========================================
    // DELETE PLATFORM TESTS
    // ===========================================

    @Test
    fun `deletePlatform succeeds for existing platform`() {
        val platformId = UUID.randomUUID()
        val platform = Platform(
            name = "TestPlatform",
            apiUrl = "https://api.test.com",
            sharedSecret = "secret"
        )

        every { platformRepository.findById(platformId) } returns Optional.of(platform)
        every { platformRepository.delete(platform) } just Runs

        assertDoesNotThrow {
            service.deletePlatform(platformId)
        }

        verify(exactly = 1) { platformRepository.delete(platform) }
    }

    @Test
    fun `deletePlatform throws ResourceNotFound for non-existent platform`() {
        val platformId = UUID.randomUUID()

        every { platformRepository.findById(platformId) } returns Optional.empty()

        assertThrows<VenuesException.ResourceNotFound> {
            service.deletePlatform(platformId)
        }

        verify(exactly = 0) { platformRepository.delete(any()) }
    }
}
