package app.venues.platform.service

import app.venues.common.exception.VenuesException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Per-platform rate limiting using Redis sliding window algorithm.
 *
 * Security: Prevents burst attacks at window boundaries (audit finding RATE-01).
 *
 * Algorithm: Sliding window using sorted set
 * 1. Add current request with timestamp score to sorted set
 * 2. Remove requests older than window (60 seconds)
 * 3. Count requests in window
 * 4. Reject if count exceeds limit
 *
 * This prevents the fixed-window burst issue where a platform could make
 * 100 requests at T=59s and another 100 at T=61s (200 in 2 seconds).
 */
@Service
class PlatformRateLimitService(
    @param:Qualifier("platformRedisTemplate")
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val WINDOW_SECONDS = 60L
        private const val KEY_PREFIX = "platform:ratelimit"
        private const val KEY_TTL_SECONDS = 120L // Keep keys for 2x window duration
    }

    /**
     * Enforce rate limit using sliding window algorithm.
     *
     * @param platformId Platform making the request
     * @param limitPerMinute Maximum requests allowed per minute (null = no limit)
     * @throws VenuesException.ValidationFailure if rate limit exceeded
     */
    fun enforce(platformId: UUID, limitPerMinute: Int?) {
        if (limitPerMinute == null || limitPerMinute <= 0) return

        val key = "$KEY_PREFIX:$platformId"
        val now = Instant.now()
        val windowStart = now.minusSeconds(WINDOW_SECONDS)

        // Use sorted set for sliding window
        val zSetOps = redisTemplate.opsForZSet()

        // Add current request with timestamp as score
        val requestId = "${now.toEpochMilli()}-${UUID.randomUUID()}"
        zSetOps.add(key, requestId, now.toEpochMilli().toDouble())

        // Remove requests older than window
        zSetOps.removeRangeByScore(key, 0.0, windowStart.toEpochMilli().toDouble())

        // Count requests in current window
        val count = zSetOps.count(key, windowStart.toEpochMilli().toDouble(), Double.MAX_VALUE) ?: 0L

        // Set expiration on key
        redisTemplate.expire(key, Duration.ofSeconds(KEY_TTL_SECONDS))
        
        if (count > limitPerMinute) {
            logger.warn { "Rate limit exceeded for platform=$platformId count=$count limit=$limitPerMinute (sliding window)" }
            throw VenuesException.ValidationFailure(
                "Rate limit exceeded ($count requests in last $WINDOW_SECONDS seconds). Maximum allowed: $limitPerMinute requests per minute."
            )
        }

        logger.debug { "Rate limit check passed: platform=$platformId count=$count limit=$limitPerMinute" }
    }
}