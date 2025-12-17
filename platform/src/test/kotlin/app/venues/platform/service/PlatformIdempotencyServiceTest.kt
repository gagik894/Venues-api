package app.venues.platform.service

import app.venues.common.exception.VenuesException
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.*

/**
 * Unit tests for PlatformIdempotencyService (Redis SET NX atomic locking).
 */
class PlatformIdempotencyServiceTest {

    private lateinit var service: PlatformIdempotencyService
    private val redisTemplate: RedisTemplate<String, String> = mockk()
    private val objectMapper: ObjectMapper = mockk()
    private val valueOps: ValueOperations<String, String> = mockk()

    private val platformId = UUID.randomUUID()
    private val idempotencyKey = "test-idempotency-key-123"
    private val endpoint = "hold"

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { redisTemplate.opsForValue() } returns valueOps
        service = PlatformIdempotencyService(redisTemplate, objectMapper)
    }

    // ===========================================
    // CACHE HIT TESTS
    // ===========================================

    @Test
    fun `withIdempotency returns cached result without execution`() {
        val cachedResponse = TestResponse("cached-result")
        val cachedJson = """{"value":"cached-result"}"""

        every { valueOps.get(any()) } returns cachedJson
        every { objectMapper.readValue(cachedJson, TestResponse::class.java) } returns cachedResponse

        var supplierCalled = false
        val result = service.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            supplierCalled = true
            TestResponse("new-result")
        }

        assertEquals("cached-result", result.value)
        assertFalse(supplierCalled)
        verify(exactly = 0) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    // ===========================================
    // LOCK ACQUISITION TESTS
    // ===========================================

    @Test
    fun `withIdempotency acquires lock and executes supplier`() {
        val newResponse = TestResponse("new-result")
        val responseJson = """{"value":"new-result"}"""

        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), eq("PROCESSING"), any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(newResponse) } returns responseJson
        every { valueOps.set(any(), responseJson, any<Duration>()) } just Runs
        every { redisTemplate.delete(any<String>()) } returns true

        var supplierCalled = false
        val result = service.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            supplierCalled = true
            newResponse
        }

        assertTrue(supplierCalled)
        assertEquals("new-result", result.value)

        verify(exactly = 1) { valueOps.setIfAbsent(any(), "PROCESSING", any<Duration>()) }
        verify(exactly = 1) { valueOps.set(any(), responseJson, any<Duration>()) }
        verify(exactly = 1) { redisTemplate.delete(any<String>()) }
    }

    @Test
    fun `withIdempotency uses correct key format`() {
        val cacheKeySlot = slot<String>()
        val lockKeySlot = slot<String>()

        every { valueOps.get(capture(cacheKeySlot)) } returns null
        every { valueOps.setIfAbsent(capture(lockKeySlot), any(), any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { valueOps.set(any(), any(), any<Duration>()) } just Runs
        every { redisTemplate.delete(any<String>()) } returns true

        service.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            TestResponse("test")
        }

        assertTrue(cacheKeySlot.captured.contains("platform:idemp"))
        assertTrue(cacheKeySlot.captured.contains(platformId.toString()))
        assertTrue(cacheKeySlot.captured.contains(endpoint))
        assertTrue(cacheKeySlot.captured.contains(idempotencyKey))

        assertTrue(lockKeySlot.captured.contains("platform:idemp:lock"))
        assertTrue(lockKeySlot.captured.contains(platformId.toString()))
    }

    // ===========================================
    // NULL/BLANK KEY TESTS
    // ===========================================

    @Test
    fun `withIdempotency bypasses cache for null key`() {
        var supplierCalled = false
        val result = service.withIdempotency(
            idempotencyKey = null,
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            supplierCalled = true
            TestResponse("direct-result")
        }

        assertTrue(supplierCalled)
        assertEquals("direct-result", result.value)

        verify(exactly = 0) { valueOps.get(any()) }
        verify(exactly = 0) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    @Test
    fun `withIdempotency bypasses cache for blank key`() {
        var supplierCalled = false
        val result = service.withIdempotency(
            idempotencyKey = "   ",
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            supplierCalled = true
            TestResponse("direct-result")
        }

        assertTrue(supplierCalled)
        assertEquals("direct-result", result.value)

        verify(exactly = 0) { valueOps.get(any()) }
    }

    @Test
    fun `withIdempotency bypasses cache for empty key`() {
        var supplierCalled = false
        val result = service.withIdempotency(
            idempotencyKey = "",
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            supplierCalled = true
            TestResponse("direct-result")
        }

        assertTrue(supplierCalled)
        assertEquals("direct-result", result.value)
    }

    // ===========================================
    // RETRY LOGIC TESTS
    // ===========================================

    @Test
    fun `withIdempotency retries and returns cached result after contention`() {
        val cachedResponse = TestResponse("cached-result")
        val cachedJson = """{"value":"cached-result"}"""

        // First call: no cache, lock fails
        // Second call: still no cache, lock fails
        // Third call: cache exists
        every { valueOps.get(any()) } returnsMany listOf(null, null, cachedJson)
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns false
        every { objectMapper.readValue(cachedJson, TestResponse::class.java) } returns cachedResponse

        var supplierCalled = false
        val result = service.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            supplierCalled = true
            TestResponse("should-not-be-called")
        }

        assertFalse(supplierCalled)
        assertEquals("cached-result", result.value)
    }

    @Test
    fun `withIdempotency throws conflict after max retries with no cache`() {
        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns false

        val exception = assertThrows<VenuesException.ResourceConflict> {
            service.withIdempotency(
                idempotencyKey = idempotencyKey,
                platformId = platformId,
                endpoint = endpoint,
                responseType = TestResponse::class.java
            ) {
                TestResponse("should-not-be-called")
            }
        }

        assertTrue(exception.message?.contains("idempotency key") == true)
        verify(exactly = 3) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    // ===========================================
    // LOCK RELEASE TESTS
    // ===========================================

    @Test
    fun `withIdempotency releases lock after successful execution`() {
        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { valueOps.set(any(), any(), any<Duration>()) } just Runs
        every { redisTemplate.delete(any<String>()) } returns true

        service.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            TestResponse("result")
        }

        verify(exactly = 1) { redisTemplate.delete(any<String>()) }
    }

    @Test
    fun `withIdempotency releases lock even if supplier throws`() {
        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { redisTemplate.delete(any<String>()) } returns true

        assertThrows<RuntimeException> {
            service.withIdempotency(
                idempotencyKey = idempotencyKey,
                platformId = platformId,
                endpoint = endpoint,
                responseType = TestResponse::class.java
            ) {
                throw RuntimeException("Supplier failed")
            }
        }

        verify(exactly = 1) { redisTemplate.delete(any<String>()) }
    }

    // ===========================================
    // CACHE WRITE FAILURE TESTS
    // ===========================================

    @Test
    fun `withIdempotency continues if cache write fails`() {
        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(any()) } throws RuntimeException("Serialization failed")
        every { redisTemplate.delete(any<String>()) } returns true

        val result = service.withIdempotency(
            idempotencyKey = idempotencyKey,
            platformId = platformId,
            endpoint = endpoint,
            responseType = TestResponse::class.java
        ) {
            TestResponse("result-despite-cache-failure")
        }

        assertEquals("result-despite-cache-failure", result.value)
    }

    // ===========================================
    // HELPER CLASSES
    // ===========================================

    data class TestResponse(val value: String)
}
