package app.venues.platform.security

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.*

/**
 * Unit tests for NonceService (replay attack prevention & stats).
 */
class NonceServiceTest {

    private lateinit var service: NonceService
    private val redisTemplate: RedisTemplate<String, String> = mockk()
    private val valueOps: ValueOperations<String, String> = mockk()

    private val platformId = UUID.randomUUID()
    private val nonce = "test-nonce-${UUID.randomUUID()}"

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { redisTemplate.opsForValue() } returns valueOps
        service = NonceService(redisTemplate)
    }

    // NEW NONCE TESTS
    @Test
    fun `isNonceUsed returns false for new nonce`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true

        val result = service.isNonceUsed(nonce, platformId)

        assertFalse(result)
        verify(exactly = 1) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
    }

    @Test
    fun `new nonce is stored with correct TTL`() {
        val durationSlot = slot<Duration>()
        every { valueOps.setIfAbsent(any(), any(), capture(durationSlot)) } returns true

        service.isNonceUsed(nonce, platformId)

        assertEquals(360L, durationSlot.captured.seconds)
    }

    @Test
    fun `new nonce key includes platform ID and nonce`() {
        val keySlot = slot<String>()
        every { valueOps.setIfAbsent(capture(keySlot), any(), any<Duration>()) } returns true

        service.isNonceUsed(nonce, platformId)

        assertTrue(keySlot.captured.contains(platformId.toString()))
        assertTrue(keySlot.captured.contains(nonce))
        assertTrue(keySlot.captured.startsWith("platform:nonce"))
    }

    // REPLAY ATTACK TESTS
    @Test
    fun `isNonceUsed returns true for already used nonce`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns false

        val result = service.isNonceUsed(nonce, platformId)

        assertTrue(result)
    }

    @Test
    fun `same nonce used by different platforms is tracked independently`() {
        val platform1 = UUID.randomUUID()
        val platform2 = UUID.randomUUID()
        val sharedNonce = "shared-nonce-123"
        val keysUsed = mutableListOf<String>()

        every { valueOps.setIfAbsent(capture(keysUsed), any(), any<Duration>()) } returns true

        service.isNonceUsed(sharedNonce, platform1)
        service.isNonceUsed(sharedNonce, platform2)

        assertEquals(2, keysUsed.size)
        assertNotEquals(keysUsed[0], keysUsed[1])
        assertTrue(keysUsed[0].contains(platform1.toString()))
        assertTrue(keysUsed[1].contains(platform2.toString()))
    }

    // ATOMIC OPERATION TESTS
    @Test
    fun `uses SET NX for atomic check-and-set`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns true

        service.isNonceUsed(nonce, platformId)

        verify(exactly = 1) { valueOps.setIfAbsent(any(), any(), any<Duration>()) }
        verify(exactly = 0) { valueOps.get(any()) }
        verify(exactly = 0) { valueOps.set(any(), any()) }
    }

    @Test
    fun `null response from Redis is treated as not used`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } returns null

        val result = service.isNonceUsed(nonce, platformId)

        assertFalse(result)
    }

    // TIMESTAMP VALUE TESTS
    @Test
    fun `stores current timestamp as value`() {
        val valueSlot = slot<String>()
        every { valueOps.setIfAbsent(any(), capture(valueSlot), any<Duration>()) } returns true

        val beforeCall = System.currentTimeMillis()
        service.isNonceUsed(nonce, platformId)
        val afterCall = System.currentTimeMillis()

        val storedTimestamp = valueSlot.captured.toLong()
        assertTrue(storedTimestamp in beforeCall..afterCall)
    }

    // STATS / CLEAR TESTS
    @Test
    fun `getNonceStats groups by platform UUID and ignores invalid keys`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val keys = setOf(
            "platform:nonce:$p1:nonce-1",
            "platform:nonce:$p1:nonce-2",
            "platform:nonce:$p2:nonce-3",
            "platform:nonce:bad-platform:nonce-4" // should be ignored
        )

        every { redisTemplate.keys("platform:nonce:*") } returns keys

        val stats = service.getNonceStats()

        assertEquals(4, stats.totalActivenonces)
        assertEquals(2, stats.noncesByPlatform[p1])
        assertEquals(1, stats.noncesByPlatform[p2])
        assertEquals(2, stats.noncesByPlatform.size) // bad key ignored
    }

    @Test
    fun `clearNoncesForPlatform deletes matching keys and returns count`() {
        val keys = setOf(
            "platform:nonce:$platformId:nonce-1",
            "platform:nonce:$platformId:nonce-2"
        )

        every { redisTemplate.keys("platform:nonce:$platformId:*") } returns keys
        every { redisTemplate.delete(keys) } returns keys.size.toLong()

        val cleared = service.clearNoncesForPlatform(platformId)

        assertEquals(2, cleared)
        verify { redisTemplate.delete(keys) }
    }
}
