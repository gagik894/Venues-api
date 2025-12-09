package app.venues.platform.service

import app.venues.common.exception.VenuesException
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.time.Duration
import java.util.*

/**
 * Unit tests for PlatformRateLimitService (sliding window algorithm).
 */
class PlatformRateLimitServiceTest {

    private lateinit var service: PlatformRateLimitService
    private val redisTemplate: RedisTemplate<String, String> = mockk()
    private val zSetOps: ZSetOperations<String, String> = mockk()

    private val platformId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { redisTemplate.opsForZSet() } returns zSetOps
        service = PlatformRateLimitService(redisTemplate)
    }

    // ===========================================
    // RATE LIMIT ENFORCEMENT TESTS
    // ===========================================

    @Test
    fun `enforce passes when under limit`() {
        every { zSetOps.add(any(), any(), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), any(), any()) } returns 0L
        every { zSetOps.count(any(), any(), any()) } returns 50L
        every { redisTemplate.expire(any(), any<Duration>()) } returns true

        assertDoesNotThrow {
            service.enforce(platformId, 100)
        }

        verify(exactly = 1) { zSetOps.add(any(), any(), any()) }
        verify(exactly = 1) { zSetOps.removeRangeByScore(any(), any(), any()) }
        verify(exactly = 1) { zSetOps.count(any(), any(), any()) }
    }

    @Test
    fun `enforce throws ValidationFailure when over limit`() {
        every { zSetOps.add(any(), any(), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), any(), any()) } returns 0L
        every { zSetOps.count(any(), any(), any()) } returns 101L
        every { redisTemplate.expire(any(), any<Duration>()) } returns true

        val exception = assertThrows<VenuesException.ValidationFailure> {
            service.enforce(platformId, 100)
        }

        assertTrue(exception.message?.contains("Rate limit exceeded") == true)
        assertTrue(exception.message?.contains("101 requests") == true)
    }

    @Test
    fun `enforce passes with null limit`() {
        assertDoesNotThrow {
            service.enforce(platformId, null)
        }

        verify(exactly = 0) { redisTemplate.opsForZSet() }
    }

    @Test
    fun `enforce passes with zero limit`() {
        assertDoesNotThrow {
            service.enforce(platformId, 0)
        }

        verify(exactly = 0) { redisTemplate.opsForZSet() }
    }

    @Test
    fun `enforce passes with negative limit`() {
        assertDoesNotThrow {
            service.enforce(platformId, -1)
        }

        verify(exactly = 0) { redisTemplate.opsForZSet() }
    }

    // ===========================================
    // SLIDING WINDOW ALGORITHM TESTS
    // ===========================================

    @Test
    fun `enforce uses correct key format`() {
        val keySlot = slot<String>()

        every { zSetOps.add(capture(keySlot), any(), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), any(), any()) } returns 0L
        every { zSetOps.count(any(), any(), any()) } returns 1L
        every { redisTemplate.expire(any(), any<Duration>()) } returns true

        service.enforce(platformId, 100)

        assertTrue(keySlot.captured.startsWith("platform:ratelimit:"))
        assertTrue(keySlot.captured.contains(platformId.toString()))
    }

    @Test
    fun `enforce adds unique request ID`() {
        val requestIdSlot = slot<String>()

        every { zSetOps.add(any(), capture(requestIdSlot), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), any(), any()) } returns 0L
        every { zSetOps.count(any(), any(), any()) } returns 1L
        every { redisTemplate.expire(any(), any<Duration>()) } returns true

        service.enforce(platformId, 100)

        // Request ID should contain timestamp and UUID
        assertTrue(requestIdSlot.captured.contains("-"))
        val parts = requestIdSlot.captured.split("-")
        assertTrue(parts[0].toLongOrNull() != null) // Timestamp part
    }

    @Test
    fun `enforce removes old requests from window`() {
        val rangeStartSlot = slot<Double>()
        val rangeEndSlot = slot<Double>()

        every { zSetOps.add(any(), any(), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), capture(rangeStartSlot), capture(rangeEndSlot)) } returns 5L
        every { zSetOps.count(any(), any(), any()) } returns 50L
        every { redisTemplate.expire(any(), any<Duration>()) } returns true

        service.enforce(platformId, 100)

        assertEquals(0.0, rangeStartSlot.captured)
        // rangeEnd should be approximately now - 60 seconds
        val now = System.currentTimeMillis()
        val windowStart = now - 60_000
        assertTrue(rangeEndSlot.captured in (windowStart - 1000).toDouble()..(windowStart + 1000).toDouble())
    }

    @Test
    fun `enforce sets key expiration`() {
        val durationSlot = slot<Duration>()

        every { zSetOps.add(any(), any(), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), any(), any()) } returns 0L
        every { zSetOps.count(any(), any(), any()) } returns 1L
        every { redisTemplate.expire(any(), capture(durationSlot)) } returns true

        service.enforce(platformId, 100)

        assertEquals(120L, durationSlot.captured.seconds) // 2x window duration
    }

    // ===========================================
    // BOUNDARY TESTS
    // ===========================================

    @Test
    fun `enforce passes at exactly the limit`() {
        every { zSetOps.add(any(), any(), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), any(), any()) } returns 0L
        every { zSetOps.count(any(), any(), any()) } returns 100L
        every { redisTemplate.expire(any(), any<Duration>()) } returns true

        assertDoesNotThrow {
            service.enforce(platformId, 100)
        }
    }

    @Test
    fun `enforce fails at limit plus one`() {
        every { zSetOps.add(any(), any(), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), any(), any()) } returns 0L
        every { zSetOps.count(any(), any(), any()) } returns 101L
        every { redisTemplate.expire(any(), any<Duration>()) } returns true

        assertThrows<VenuesException.ValidationFailure> {
            service.enforce(platformId, 100)
        }
    }

    @Test
    fun `enforce works for different platforms independently`() {
        val platform1 = UUID.randomUUID()
        val platform2 = UUID.randomUUID()
        val keysUsed = mutableListOf<String>()

        every { zSetOps.add(capture(keysUsed), any(), any()) } returns true
        every { zSetOps.removeRangeByScore(any(), any(), any()) } returns 0L
        every { zSetOps.count(any(), any(), any()) } returns 1L
        every { redisTemplate.expire(any(), any<Duration>()) } returns true

        service.enforce(platform1, 100)
        service.enforce(platform2, 100)

        assertEquals(2, keysUsed.size)
        assertNotEquals(keysUsed[0], keysUsed[1])
        assertTrue(keysUsed[0].contains(platform1.toString()))
        assertTrue(keysUsed[1].contains(platform2.toString()))
    }
}
