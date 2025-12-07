package app.venues.platform.service

import app.venues.common.exception.VenuesException
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

/**
 * Idempotency helper keyed by platform + endpoint + Idempotency-Key.
 *
 * Security: Uses atomic Redis SET NX (set if not exists) to prevent duplicate execution
 * of concurrent requests with the same idempotency key (audit finding CRIT-04).
 *
 * Implementation:
 * 1. Attempt to acquire exclusive lock using SET NX with 30s TTL
 * 2. If lock acquired, execute operation and cache result for 24h
 * 3. If lock not acquired, retry with exponential backoff up to 3 times
 * 4. After retries, check if result is cached (previous request completed)
 *
 * This prevents race conditions where two concurrent requests with the same
 * idempotency key could both execute before the cache is populated.
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
        private const val LOCK_PREFIX = "platform:idemp:lock"
        private val RESULT_TTL: Duration = Duration.ofHours(24)
        private val LOCK_TTL: Duration = Duration.ofSeconds(30)
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 100L
    }

    /**
     * Execute supplier with idempotency protection.
     *
     * @param idempotencyKey Optional idempotency key from client
     * @param platformId Platform making the request
     * @param endpoint API endpoint (for key namespacing)
     * @param responseType Expected response type for deserialization
     * @param supplier Function to execute if not cached
     * @return Result of supplier (either freshly computed or from cache)
     */
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
        val lockKey = "$LOCK_PREFIX:$platformId:$endpoint:$idempotencyKey"

        // Fast-path: return cached result if exists
        redisTemplate.opsForValue().get(cacheKey)?.let { cached ->
            logger.debug { "Idempotency cache hit: platform=$platformId endpoint=$endpoint key=$idempotencyKey" }
            return objectMapper.readValue(cached, responseType)
        }

        // Attempt to acquire exclusive lock with retries
        var attempt = 0
        while (attempt < MAX_RETRY_ATTEMPTS) {
            val lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "PROCESSING", LOCK_TTL) ?: false

            if (lockAcquired) {
                // Lock acquired - execute operation
                logger.debug { "Idempotency lock acquired: platform=$platformId endpoint=$endpoint key=$idempotencyKey" }

                try {
                    val result = supplier()

                    // Cache result for 24 hours
                    try {
                        val payload = objectMapper.writeValueAsString(result)
                        redisTemplate.opsForValue().set(cacheKey, payload, RESULT_TTL)
                        logger.debug { "Idempotency result cached: platform=$platformId endpoint=$endpoint key=$idempotencyKey" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to cache idempotent response for platform=$platformId endpoint=$endpoint" }
                    }

                    return result
                } finally {
                    // Release lock
                    redisTemplate.delete(lockKey)
                    logger.debug { "Idempotency lock released: platform=$platformId endpoint=$endpoint key=$idempotencyKey" }
                }
            }

            // Lock not acquired - another request is processing
            logger.debug { "Idempotency lock contention (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS): platform=$platformId endpoint=$endpoint key=$idempotencyKey" }

            // Exponential backoff
            val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl attempt)
            Thread.sleep(delayMs)

            // Check if result is now cached (other request completed)
            redisTemplate.opsForValue().get(cacheKey)?.let { cached ->
                logger.debug { "Idempotency cache hit after retry: platform=$platformId endpoint=$endpoint key=$idempotencyKey" }
                return objectMapper.readValue(cached, responseType)
            }

            attempt++
        }

        // All retries exhausted and no cached result - fail with conflict error
        logger.warn { "Idempotency lock timeout after $MAX_RETRY_ATTEMPTS attempts: platform=$platformId endpoint=$endpoint key=$idempotencyKey" }
        throw VenuesException.ResourceConflict(
            "Another request with the same idempotency key is being processed. Please retry in a moment."
        )
    }
}