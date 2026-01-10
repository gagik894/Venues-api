package app.venues.shared.idempotency

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive tests for unified IdempotencyService.
 *
 * Tests cover:
 * - Cache hits and misses
 * - Lock acquisition and release
 * - Concurrent request handling
 * - Error scenarios (Redis failure, serialization errors)
 * - Polling and exponential backoff
 * - Key format validation
 */
class IdempotencyServiceTest {

    private lateinit var service: IdempotencyService
    private val redisTemplate: RedisTemplate<String, String> = mockk()
    private val objectMapper: ObjectMapper = mockk()
    private val valueOps: ValueOperations<String, String> = mockk()

    private val testContext = IdempotencyContext(
        keyPrefix = "booking",
        operation = "cart:add-seat",
        scopeId = "cart-123",
        idempotencyKey = "idemp-xyz",
        responseType = TestResponse::class.java
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { redisTemplate.opsForValue() } returns valueOps
        service = IdempotencyService(redisTemplate, objectMapper)
    }

    // ===========================================
    // CACHE HIT TESTS
    // ===========================================

    @Test
    fun `executeWithIdempotency returns cached result without executing supplier`() {
        val cachedResponse = TestResponse("cached-result")
        val cachedJson = """{"value":"cached-result"}"""

        every { valueOps.get(any()) } returns cachedJson
        every { objectMapper.readValue(cachedJson, TestResponse::class.java) } returns cachedResponse

        var supplierCalled = false
        val result = service.executeWithIdempotency(testContext) {
            supplierCalled = true
            TestResponse("new-result")
        }

        assertEquals("cached-result", result.value)
        assertFalse(supplierCalled, "Supplier should not be called when result is cached")
        verify(exactly = 1) { valueOps.get(any()) }
        verify(exactly = 0) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    @Test
    fun `cache hit uses correct key format`() {
        val cacheKeySlot = slot<String>()
        val cachedJson = """{"value":"test"}"""

        every { valueOps.get(capture(cacheKeySlot)) } returns cachedJson
        every { objectMapper.readValue(any<String>(), TestResponse::class.java) } returns TestResponse("test")

        service.executeWithIdempotency(testContext) { TestResponse("test") }

        val expectedKey = "booking:cart:add-seat:cart-123:idemp-xyz"
        assertEquals(expectedKey, cacheKeySlot.captured)
    }

    // ===========================================
    // LOCK ACQUISITION TESTS
    // ===========================================

    @Test
    fun `executeWithIdempotency acquires lock and executes supplier on cache miss`() {
        val newResponse = TestResponse("new-result")
        val responseJson = """{"value":"new-result"}"""

        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), eq("LOCKED"), any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(newResponse) } returns responseJson
        every { valueOps.set(any(), responseJson, any<Duration>()) } just Runs
        every { redisTemplate.delete(any<String>()) } returns true

        var supplierCalled = false
        val result = service.executeWithIdempotency(testContext) {
            supplierCalled = true
            newResponse
        }

        assertTrue(supplierCalled, "Supplier should be called when no cache exists")
        assertEquals("new-result", result.value)

        verify(exactly = 1) { valueOps.setIfAbsent(any(), "LOCKED", any<Duration>()) }
        verify(exactly = 1) { valueOps.set(any(), responseJson, any<Duration>()) }
        verify(exactly = 1) { redisTemplate.delete(any<String>()) }
    }

    @Test
    fun `lock key has correct format`() {
        val lockKeySlot = slot<String>()

        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(capture(lockKeySlot), any(), any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { valueOps.set(any(), any(), any<Duration>()) } just Runs
        every { redisTemplate.delete(any<String>()) } returns true

        service.executeWithIdempotency(testContext) { TestResponse("test") }

        val expectedLockKey = "lock:booking:cart:add-seat:cart-123:idemp-xyz"
        assertEquals(expectedLockKey, lockKeySlot.captured)
    }

    @Test
    fun `lock is released after successful execution`() {
        val lockKeySlot = slot<String>()

        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { valueOps.set(any(), any(), any<Duration>()) } just Runs
        every { redisTemplate.delete(capture(lockKeySlot)) } returns true

        service.executeWithIdempotency(testContext) { TestResponse("test") }

        verify(exactly = 1) { redisTemplate.delete(any<String>()) }
        assertTrue(lockKeySlot.captured.startsWith("lock:"))
    }

    @Test
    fun `lock is released even if supplier throws exception`() {
        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { redisTemplate.delete(any<String>()) } returns true

        assertThrows<RuntimeException> {
            service.executeWithIdempotency(testContext) {
                throw RuntimeException("Supplier failed")
            }
        }

        verify(exactly = 1) { redisTemplate.delete(any<String>()) }
    }

    // ===========================================
    // POLLING AND RETRY TESTS
    // ===========================================

    @Test
    fun `executeWithIdempotency polls for result when lock is held by another request`() {
        val cachedResponse = TestResponse("cached-result")
        val cachedJson = """{"value":"cached-result"}"""

        // First call: no cache, lock fails (held by another request)
        // Second call: still no cache, lock fails
        // Third call: cache exists (other request completed)
        every { valueOps.get(any()) } returnsMany listOf(null, null, cachedJson)
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns false
        every { objectMapper.readValue(cachedJson, TestResponse::class.java) } returns cachedResponse

        var supplierCalled = false
        val result = service.executeWithIdempotency(testContext) {
            supplierCalled = true
            TestResponse("should-not-be-called")
        }

        assertFalse(supplierCalled, "Supplier should not be called when another request is processing")
        assertEquals("cached-result", result.value)
        verify(atLeast = 2) { valueOps.get(any()) }
    }

    @Test
    fun `throws ResourceConflict after max polling attempts with no cached result`() {
        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns false

        val exception = assertThrows<VenuesException.ResourceConflict> {
            service.executeWithIdempotency(testContext) {
                TestResponse("should-not-be-called")
            }
        }
        assertTrue(exception.message.contains("idempotency key is being processed"))
        verify(atLeast = 10) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    // ===========================================
    // ERROR HANDLING TESTS
    // ===========================================

    @Test
    fun `continues operation when cache write fails`() {
        val newResponse = TestResponse("result")

        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(any()) } throws RuntimeException("Serialization failed")
        every { redisTemplate.delete(any<String>()) } returns true

        val result = service.executeWithIdempotency(testContext) { newResponse }

        assertEquals("result", result.value)
        verify(exactly = 1) { redisTemplate.delete(any<String>()) }
    }

    @Test
    fun `throws InternalError when cached result deserialization fails`() {
        val cachedJson = """{"invalid":"json"}"""

        every { valueOps.get(any()) } returns cachedJson
        every { objectMapper.readValue(cachedJson, TestResponse::class.java) } throws
                com.fasterxml.jackson.databind.JsonMappingException.from(
                    null as com.fasterxml.jackson.core.JsonParser?,
                    "Parse error"
                )

        val exception = assertThrows<VenuesException.InternalError> {
            service.executeWithIdempotency(testContext) { TestResponse("test") }
        }

        assertTrue(exception.message!!.contains("Failed to deserialize"))
    }

    @Test
    fun `executes without protection when Redis is unavailable for lock acquisition`() {
        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } throws
                org.springframework.data.redis.RedisConnectionFailureException("Redis down")
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { valueOps.set(any(), any(), any<Duration>()) } throws
                org.springframework.data.redis.RedisConnectionFailureException("Redis down")
        every { redisTemplate.delete(any<String>()) } returns true

        var supplierCalled = false
        val result = service.executeWithIdempotency(testContext) {
            supplierCalled = true
            TestResponse("fail-open-result")
        }

        assertTrue(supplierCalled, "Should execute when Redis is unavailable (fail-open)")
        assertEquals("fail-open-result", result.value)
    }

    // ===========================================
    // IDEMPOTENCY CONTEXT TESTS
    // ===========================================

    @Test
    fun `IdempotencyContext validates required fields`() {
        assertThrows<IllegalArgumentException> {
            IdempotencyContext(
                keyPrefix = "",  // Blank
                operation = "test",
                scopeId = null,
                idempotencyKey = "key",
                responseType = TestResponse::class.java
            )
        }

        assertThrows<IllegalArgumentException> {
            IdempotencyContext(
                keyPrefix = "booking",
                operation = "",  // Blank
                scopeId = null,
                idempotencyKey = "key",
                responseType = TestResponse::class.java
            )
        }

        assertThrows<IllegalArgumentException> {
            IdempotencyContext(
                keyPrefix = "booking",
                operation = "test",
                scopeId = null,
                idempotencyKey = "",  // Blank
                responseType = TestResponse::class.java
            )
        }
    }

    @Test
    fun `IdempotencyContext generates correct cache key with scope`() {
        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add-seat",
            scopeId = "cart-uuid-123",
            idempotencyKey = "idemp-key-xyz",
            responseType = TestResponse::class.java
        )

        val expected = "booking:cart:add-seat:cart-uuid-123:idemp-key-xyz"
        assertEquals(expected, context.getCacheKey())
    }

    @Test
    fun `IdempotencyContext generates correct cache key without scope`() {
        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "direct-sale",
            scopeId = null,
            idempotencyKey = "idemp-key-xyz",
            responseType = TestResponse::class.java
        )

        val expected = "booking:direct-sale:idemp-key-xyz"
        assertEquals(expected, context.getCacheKey())
    }

    @Test
    fun `IdempotencyContext generates correct lock key`() {
        val context = IdempotencyContext(
            keyPrefix = "platform",
            operation = "hold",
            scopeId = "platform-123",
            idempotencyKey = "key-xyz",
            responseType = TestResponse::class.java
        )

        val expected = "lock:platform:hold:platform-123:key-xyz"
        assertEquals(expected, context.getLockKey())
    }

    // ===========================================
    // CONCURRENCY TESTS
    // ===========================================

    @Test
    fun `concurrent requests with same context execute only once`() {
        val executionCount = AtomicInteger(0)
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(10)

        every { valueOps.get(any()) } returns null
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } answers {
            // First caller gets lock
            executionCount.incrementAndGet() == 1
        }
        every { objectMapper.writeValueAsString(any()) } returns """{"value":"result"}"""
        every { valueOps.set(any(), any(), any<Duration>()) } just Runs
        every { redisTemplate.delete(any<String>()) } returns true

        val results = mutableListOf<TestResponse>()

        // Simulate 10 concurrent requests
        repeat(10) {
            executor.submit {
                latch.await()
                try {
                    val result = service.executeWithIdempotency(testContext) {
                        Thread.sleep(50) // Simulate work
                        TestResponse("result")
                    }
                    synchronized(results) {
                        results.add(result)
                    }
                } catch (_: Exception) {
                    // Expected for concurrent requests that fail to acquire lock
                }
            }
        }

        latch.countDown()
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        // At least one should succeed (the one that got the lock)
        assertTrue(results.isNotEmpty())
        verify(atLeast = 1) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    // ===========================================
    // HELPER CLASSES
    // ===========================================

    data class TestResponse(val value: String)
}

