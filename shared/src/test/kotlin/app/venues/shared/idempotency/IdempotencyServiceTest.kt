package app.venues.shared.idempotency

import app.venues.common.exception.VenuesException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

@ExtendWith(MockKExtension::class)
class IdempotencyServiceTest {

    private val redisTemplate: RedisTemplate<String, String> = mockk(relaxed = true)
    private val valueOps: ValueOperations<String, String> = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper() // Use real mapper
    private val service = IdempotencyService(redisTemplate, objectMapper)

    private data class DummyResponse(val value: String)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `returns cached value without executing supplier`() {
        every { redisTemplate.opsForValue() } returns valueOps

        // Mock cached legacy JSON (no wrapper)
        every { valueOps.get("booking:cart:add:scope-1:idem-1") } returns "{\"value\":\"cached\"}"

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-1",
            responseType = DummyResponse::class.java
        )

        val result = service.executeWithIdempotency(context) {
            throw IllegalStateException("Should not run")
        }

        assertEquals(DummyResponse("cached"), result.data)
        verify(exactly = 0) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    @Test
    fun `acquires lock executes supplier and caches result`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-2") } returns null
        every { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-2", "LOCKED", any<Duration>()) } returns true
        val slot = slot<String>()
        every { valueOps.set("booking:cart:add:scope-1:idem-2", capture(slot), any<Duration>()) } just Runs
        justRun { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-2") }

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-2",
            responseType = DummyResponse::class.java
        )

        val result = service.executeWithIdempotency(context) {
            DummyResponse("fresh")
        }

        assertEquals(DummyResponse("fresh"), result.data)

        //Verify that what was cached is valid JSON wrapper or legacy
        // Ideally we expect wrapper now
        verify(exactly = 1) { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-2", "LOCKED", any<Duration>()) }
        verify(exactly = 1) { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-2") }
    }

    @Test
    fun `releases lock even when supplier fails`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-3") } returns null
        every { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-3", "LOCKED", any<Duration>()) } returns true
        justRun { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-3") }

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-3",
            responseType = DummyResponse::class.java
        )

        assertThrows(IllegalStateException::class.java) {
            service.executeWithIdempotency(context) {
                throw IllegalStateException("boom")
            }
        }

        verify(exactly = 1) { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-3") }
        verify(exactly = 0) { valueOps.set("booking:cart:add:scope-1:idem-3", any(), any<Duration>()) }
    }

    @Test
    fun `polls for cached value when lock is held`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-4") } returnsMany listOf(
            null,
            null,
            "{\"value\":\"from-cache\"}"
        )
        every { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-4", "LOCKED", any<Duration>()) } returns false

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-4",
            responseType = DummyResponse::class.java
        )

        val result = service.executeWithIdempotency(context) {
            throw IllegalStateException("Should not run when lock held")
        }

        assertEquals(DummyResponse("from-cache"), result.data)
        verify(exactly = 1) { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-4", "LOCKED", any<Duration>()) }
    }

    @Test
    fun `throws conflict after exhausting poll attempts`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-5") } returnsMany List(11) { null }
        every { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-5", "LOCKED", any<Duration>()) } returns false

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-5",
            responseType = DummyResponse::class.java
        )

        assertThrows(VenuesException.ResourceConflict::class.java) {
            service.executeWithIdempotency(context) {
                throw IllegalStateException("Should not run when lock held")
            }
        }
    }

    @Test
    fun `throws conflict when request hash mismatches`() {
        every { redisTemplate.opsForValue() } returns valueOps

        // Mock a cached entry in the NEW format (wrapper)
        // Cached hash = "old-hash", Result = {"value":"cached"}
        // Escaping JSON for test:
        // Result field in wrapper is a String (serialized JSON of DummyResponse)
        val innerJson = "{\"value\":\"cached\"}"
        val wrapperJson = objectMapper.writeValueAsString(
            mapOf(
                "result" to innerJson,
                "requestHash" to "old-hash"
            )
        )

        every { valueOps.get("booking:cart:add:scope-1:idem-hash") } returns wrapperJson

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-hash",
            responseType = DummyResponse::class.java,
            requestHash = "new-hash" // Mismatch!
        )

        assertThrows(VenuesException.ResourceConflict::class.java) {
            service.executeWithIdempotency(context) {
                DummyResponse("should-not-run")
            }
        }
    }

    @Test
    fun `succeeds when request hash matches`() {
        every { redisTemplate.opsForValue() } returns valueOps

        val innerJson = "{\"value\":\"cached\"}"
        val wrapperJson = objectMapper.writeValueAsString(
            mapOf(
                "result" to innerJson,
                "requestHash" to "matching-hash"
            )
        )

        every { valueOps.get("booking:cart:add:scope-1:idem-match") } returns wrapperJson

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-match",
            responseType = DummyResponse::class.java,
            requestHash = "matching-hash" // Match!
        )

        val result = service.executeWithIdempotency(context) {
            throw IllegalStateException("Should not run")
        }

        assertEquals(DummyResponse("cached"), result.data)
    }

    @Test
    fun `fails open when redis unavailable during lock`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-2:idem-6") } returns null
        every {
            valueOps.setIfAbsent(
                "lock:booking:cart:add:scope-2:idem-6",
                "LOCKED",
                any<Duration>()
            )
        } throws RedisConnectionFailureException("down")

        justRun { valueOps.set("booking:cart:add:scope-2:idem-6", any(), any<Duration>()) }
        justRun { redisTemplate.delete("lock:booking:cart:add:scope-2:idem-6") }

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-2",
            idempotencyKey = "idem-6",
            responseType = DummyResponse::class.java
        )

        val result = service.executeWithIdempotency(context) {
            DummyResponse("ok")
        }

        assertEquals(DummyResponse("ok"), result.data)
        verify(exactly = 1) { redisTemplate.delete("lock:booking:cart:add:scope-2:idem-6") }
    }

    @Test
    fun `throws internal error when cached payload cannot be deserialized`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-7") } returns "{ invalid json }"
        // The real object mapper will throw JsonProcessingException if the format is bad
        // We don't need to mock it explicitly to throw, just ensure the input is bad.

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-7",
            responseType = DummyResponse::class.java
        )

        assertThrows(VenuesException.InternalError::class.java) {
            service.executeWithIdempotency(context) {
                DummyResponse("should-not-run")
            }
        }
    }

    @Test
    fun `returns result even when serialization for cache fails`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-8") } returns null
        every { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-8", "LOCKED", any<Duration>()) } returns true
        // Mock the ObjectMapper to throw when writing value as string
        // This is tricky with a real ObjectMapper. We'd need to mock the ObjectMapper itself
        // or use a spy. For this test, we'll assume the service handles the exception
        // if the real mapper throws.
        // Since we're using a real ObjectMapper, we can't easily make writeValueAsString throw
        // without mocking the ObjectMapper itself, which defeats the purpose of using a real one.
        // For this specific test, we'll simulate the failure by having `valueOps.set` not called.
        // The service should catch the JsonProcessingException from `objectMapper.writeValueAsString`
        // and proceed without caching.
        // So, we don't need to mock `objectMapper.writeValueAsString` to throw.
        // Instead, we verify `valueOps.set` is not called.

        justRun { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-8") }

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-8",
            responseType = DummyResponse::class.java
        )

        // To make `objectMapper.writeValueAsString` fail, we would need to mock the `objectMapper`
        // instance itself, or use a spy. Given the instruction to use a real mapper,
        // this test case might need to be re-evaluated or the `objectMapper` temporarily mocked
        // for this specific scenario.
        // For now, we'll assume the service handles the exception if it occurs,
        // and the key is not set.
        // Let's make the `objectMapper` a spy for this test to simulate the failure.
        val spiedObjectMapper = spyk(jacksonObjectMapper())
        val spiedService = IdempotencyService(redisTemplate, spiedObjectMapper)

        every { spiedObjectMapper.writeValueAsString(DummyResponse("ok")) } throws object :
            JsonProcessingException("fail") {}

        val result = spiedService.executeWithIdempotency(context) {
            DummyResponse("ok")
        }

        assertEquals(DummyResponse("ok"), result.data)
        verify(exactly = 0) { valueOps.set("booking:cart:add:scope-1:idem-8", any(), any<Duration>()) }
        verify(exactly = 1) { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-8") }
    }

    @Test
    fun `throws ResourceConflict when idempotency key reused with different request body`() {
        every { redisTemplate.opsForValue() } returns valueOps

        // Simulate cached entry with a different hash
        val originalHash = "abc123originalHash"
        val differentHash = "def456differentHash"

        val cachedEntry = """
            {
                "result": "{\"value\":\"original\"}",
                "requestHash": "$originalHash"
            }
        """.trimIndent()

        every { valueOps.get("booking:cart:add:scope-1:idem-collision") } returns cachedEntry

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-collision",
            responseType = DummyResponse::class.java,
            requestHash = differentHash  // Different hash means different request body
        )

        val exception = assertThrows(VenuesException.ResourceConflict::class.java) {
            service.executeWithIdempotency(context) {
                DummyResponse("should-not-run")
            }
        }

        assertEquals(
            "Idempotency key has already been used with a different request body",
            exception.message
        )
    }

    @Test
    fun `returns cached result when idempotency key reused with same request body hash`() {
        every { redisTemplate.opsForValue() } returns valueOps

        // Simulate cached entry with matching hash
        val matchingHash = "abc123matchingHash"

        val cachedEntry = """
            {
                "result": "{\"value\":\"cached\"}",
                "requestHash": "$matchingHash"
            }
        """.trimIndent()

        every { valueOps.get("booking:cart:add:scope-1:idem-same-hash") } returns cachedEntry

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-same-hash",
            responseType = DummyResponse::class.java,
            requestHash = matchingHash  // Same hash = same request body
        )

        val result = service.executeWithIdempotency(context) {
            throw IllegalStateException("Should not run - should return cached")
        }

        assertEquals(DummyResponse("cached"), result.data)
        assertEquals(true, result.isFromCache)
    }

    @Test
    fun `executes without lock when Redis connection fails (fail-open)`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-redis-fail") } returns null
        every {
            valueOps.setIfAbsent(
                "lock:booking:cart:add:scope-1:idem-redis-fail",
                "LOCKED",
                any<Duration>()
            )
        } throws
                RedisConnectionFailureException("Redis is down")

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-redis-fail",
            responseType = DummyResponse::class.java
        )

        var executed = false
        val result = service.executeWithIdempotency(context) {
            executed = true
            DummyResponse("executed-despite-redis-failure")
        }

        assertEquals(DummyResponse("executed-despite-redis-failure"), result.data)
        assertEquals(true, executed, "Operation should execute when Redis fails (fail-open)")
        assertEquals(false, result.isFromCache)
    }

    @Test
    fun `executes without lock when Redis times out (fail-open)`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-timeout") } returns null
        every { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-timeout", "LOCKED", any<Duration>()) } throws
                io.lettuce.core.RedisCommandTimeoutException("Redis timeout")

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-timeout",
            responseType = DummyResponse::class.java
        )

        var executed = false
        val result = service.executeWithIdempotency(context) {
            executed = true
            DummyResponse("executed-despite-timeout")
        }

        assertEquals(DummyResponse("executed-despite-timeout"), result.data)
        assertEquals(true, executed, "Operation should execute when Redis times out (fail-open)")
        assertEquals(false, result.isFromCache)
    }

    @Test
    fun `returns cached result when old entry has null hash and new request has hash`() {
        every { redisTemplate.opsForValue() } returns valueOps

        // Old cached entry without hash (backward compatibility)
        val cachedEntry = """
            {
                "result": "{\"value\":\"legacy-cached\"}",
                "requestHash": null
            }
        """.trimIndent()

        every { valueOps.get("booking:cart:add:scope-1:idem-null-hash") } returns cachedEntry

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-null-hash",
            responseType = DummyResponse::class.java,
            requestHash = "abc123newHash"  // New request has hash, but old entry doesn't
        )

        val result = service.executeWithIdempotency(context) {
            throw IllegalStateException("Should not run - should return cached")
        }

        // Should return cached result (backward compatible - no hash validation)
        assertEquals(DummyResponse("legacy-cached"), result.data)
        assertEquals(true, result.isFromCache)
    }

    @Test
    fun `returns cached result when new request has null hash but old entry has hash`() {
        every { redisTemplate.opsForValue() } returns valueOps

        // Old cached entry with hash
        val cachedEntry = """
            {
                "result": "{\"value\":\"cached-with-hash\"}",
                "requestHash": "abc123oldHash"
            }
        """.trimIndent()

        every { valueOps.get("booking:cart:add:scope-1:idem-new-no-hash") } returns cachedEntry

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-new-no-hash",
            responseType = DummyResponse::class.java,
            requestHash = null  // New request has no hash
        )

        val result = service.executeWithIdempotency(context) {
            throw IllegalStateException("Should not run - should return cached")
        }

        // Should return cached result (no hash validation when new request lacks hash)
        assertEquals(DummyResponse("cached-with-hash"), result.data)
        assertEquals(true, result.isFromCache)
    }

    @Test
    fun `handles legacy cached entries without wrapper (backward compatibility)`() {
        every { redisTemplate.opsForValue() } returns valueOps

        // Legacy entry: just raw JSON, no wrapper
        every { valueOps.get("booking:cart:add:scope-1:idem-legacy") } returns "{\"value\":\"legacy-format\"}"

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-legacy",
            responseType = DummyResponse::class.java,
            requestHash = "some-hash"
        )

        val result = service.executeWithIdempotency(context) {
            throw IllegalStateException("Should not run - should return cached")
        }

        assertEquals(DummyResponse("legacy-format"), result.data)
        assertEquals(true, result.isFromCache)
    }

    @Test
    fun `returns null when Redis times out during cache read`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-read-timeout") } throws
                io.lettuce.core.RedisCommandTimeoutException("Timeout reading cache")
        every {
            valueOps.setIfAbsent(
                "lock:booking:cart:add:scope-1:idem-read-timeout",
                "LOCKED",
                any<Duration>()
            )
        } returns true
        justRun { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-read-timeout") }

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-read-timeout",
            responseType = DummyResponse::class.java
        )

        var executed = false
        val result = service.executeWithIdempotency(context) {
            executed = true
            DummyResponse("executed-after-cache-timeout")
        }

        assertEquals(true, executed, "Should execute when cache read times out")
        assertEquals(DummyResponse("executed-after-cache-timeout"), result.data)
        assertEquals(false, result.isFromCache)
    }

    @Test
    fun `succeeds even when cache write fails`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-write-fail") } returns null
        every {
            valueOps.setIfAbsent(
                "lock:booking:cart:add:scope-1:idem-write-fail",
                "LOCKED",
                any<Duration>()
            )
        } returns true
        every { valueOps.set("booking:cart:add:scope-1:idem-write-fail", any(), any<Duration>()) } throws
                RedisConnectionFailureException("Cannot write to cache")
        justRun { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-write-fail") }

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-write-fail",
            responseType = DummyResponse::class.java
        )

        var executed = false
        val result = service.executeWithIdempotency(context) {
            executed = true
            DummyResponse("succeeded-despite-cache-failure")
        }

        assertEquals(true, executed)
        assertEquals(DummyResponse("succeeded-despite-cache-failure"), result.data)
        assertEquals(false, result.isFromCache)
        // Operation succeeds even though caching failed
    }

    // Mock successful wrapper parse
    // We can't mock private inner class easily, so we rely on lenient mocking or refactor.
    // Or we assume the real ObjectMapper would work if we didn't mock it.
    // Since ObjectMapper is mocked, we must mock the readValue call for the wrapper.
    // BUT the wrapper is private. Ideally we shouldn't mock ObjectMapper for data classes.
    // Limitation: Mocking private data class deserialization is hard.
    // Strategy: Mock readValue to throw, forcing fallback? No, we want to test validation.
    // Better: Don't mock ObjectMapper. Use a real one.
}
