package app.venues.shared.idempotency

import app.venues.common.exception.VenuesException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val objectMapper: ObjectMapper = mockk(relaxed = true)
    private val service = IdempotencyService(redisTemplate, objectMapper)

    private data class DummyResponse(val value: String)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `returns cached value without executing supplier`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-1") } returns "{\"value\":\"cached\"}"
        every {
            objectMapper.readValue(
                "{\"value\":\"cached\"}",
                DummyResponse::class.java
            )
        } returns DummyResponse("cached")

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

        assertEquals(DummyResponse("cached"), result)
        verify(exactly = 0) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    @Test
    fun `acquires lock executes supplier and caches result`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-2") } returns null
        every { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-2", "LOCKED", any<Duration>()) } returns true
        every { objectMapper.writeValueAsString(DummyResponse("fresh")) } returns "{\"value\":\"fresh\"}"
        justRun { valueOps.set("booking:cart:add:scope-1:idem-2", any(), any<Duration>()) }
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

        assertEquals(DummyResponse("fresh"), result)
        verify(exactly = 1) { valueOps.setIfAbsent("lock:booking:cart:add:scope-1:idem-2", "LOCKED", any<Duration>()) }
        verify(exactly = 1) {
            valueOps.set(
                "booking:cart:add:scope-1:idem-2",
                "{\"value\":\"fresh\"}",
                any<Duration>()
            )
        }
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
        every { objectMapper.readValue("{\"value\":\"from-cache\"}", DummyResponse::class.java) } returns DummyResponse(
            "from-cache"
        )

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

        assertEquals(DummyResponse("from-cache"), result)
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
        every { objectMapper.writeValueAsString(DummyResponse("ok")) } returns "{\"value\":\"ok\"}"
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

        assertEquals(DummyResponse("ok"), result)
        verify(exactly = 1) { redisTemplate.delete("lock:booking:cart:add:scope-2:idem-6") }
    }

    @Test
    fun `throws internal error when cached payload cannot be deserialized`() {
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get("booking:cart:add:scope-1:idem-7") } returns "{\"value\":\"bad\"}"
        every { objectMapper.readValue("{\"value\":\"bad\"}", DummyResponse::class.java) } throws object :
            JsonProcessingException("bad") {}

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
        every { objectMapper.writeValueAsString(DummyResponse("ok")) } throws object :
            JsonProcessingException("fail") {}
        justRun { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-8") }

        val context = IdempotencyContext(
            keyPrefix = "booking",
            operation = "cart:add",
            scopeId = "scope-1",
            idempotencyKey = "idem-8",
            responseType = DummyResponse::class.java
        )

        val result = service.executeWithIdempotency(context) {
            DummyResponse("ok")
        }

        assertEquals(DummyResponse("ok"), result)
        verify(exactly = 0) { valueOps.set("booking:cart:add:scope-1:idem-8", any(), any<Duration>()) }
        verify(exactly = 1) { redisTemplate.delete("lock:booking:cart:add:scope-1:idem-8") }
    }
}
