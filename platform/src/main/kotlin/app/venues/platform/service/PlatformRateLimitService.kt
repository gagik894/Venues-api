package app.venues.platform.service

import app.venues.common.exception.VenuesException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

/**
 * Per-platform rate limiting using Redis (simple fixed window per minute).
 * Rejects when the configured limit is exceeded.
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
    }

    fun enforce(platformId: UUID, limitPerMinute: Int?) {
        if (limitPerMinute == null || limitPerMinute <= 0) return

        val key = "$KEY_PREFIX:$platformId"
        val count = redisTemplate.opsForValue().increment(key) ?: 0

        if (count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS))
        }

        if (count > limitPerMinute) {
            logger.warn { "Rate limit exceeded for platform=$platformId count=$count limit=$limitPerMinute" }
            throw VenuesException.ValidationFailure("Rate limit exceeded. Please retry later.")
        }
    }
}