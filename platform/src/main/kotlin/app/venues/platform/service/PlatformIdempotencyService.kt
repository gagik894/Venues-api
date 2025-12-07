package app.venues.platform.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

/**
 * Idempotency helper keyed by platform + endpoint + Idempotency-Key.
 * Stores serialized responses in Redis for 24h.
 */
@Service
class PlatformIdempotencyService(
    @param:Qualifier("platformRedisTemplate")
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val KEY_PREFIX = "platform:idemp"
        private val TTL: Duration = Duration.ofHours(24)
    }

    fun <T : Any> withIdempotency(
        idempotencyKey: String?,
        platformId: UUID,
        endpoint: String,
        responseType: Class<T>,
        supplier: () -> T
    ): T {
        if (idempotencyKey.isNullOrBlank()) {
            return supplier()
        }

        val cacheKey = "$KEY_PREFIX:$platformId:$endpoint:$idempotencyKey"

        // Fast-path: return cached
        redisTemplate.opsForValue().get(cacheKey)?.let { cached ->
            return objectMapper.readValue(cached, responseType)
        }

        val result = supplier()
        try {
            val payload = objectMapper.writeValueAsString(result)
            redisTemplate.opsForValue().set(cacheKey, payload, TTL)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cache idempotent response for platform=$platformId endpoint=$endpoint" }
        }
        return result
    }
}