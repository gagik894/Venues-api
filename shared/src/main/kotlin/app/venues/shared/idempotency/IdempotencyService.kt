package app.venues.shared.idempotency

import app.venues.common.exception.VenuesException
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

/**
 * Generic idempotency service for protecting API endpoints from duplicate execution.
 *
 * Security: Uses atomic Redis SET NX (set if not exists) to prevent duplicate execution
 * of concurrent requests with the same idempotency key.
 *
 * Implementation:
 * 1. Attempt to acquire exclusive lock using SET NX with 30s TTL
 * 2. If lock acquired, execute operation and cache result for 24h
 * 3. If lock not acquired, retry with exponential backoff up to 3 times
 * 4. After retries, check if result is cached (previous request completed)
 *
 * This prevents race conditions where two concurrent requests with the same
 * idempotency key could both execute before the cache is populated.
 *
 * Key Format: "{keyPrefix}:{namespace}:{idempotencyKey}"
 * - keyPrefix: Module-specific prefix (e.g., "booking", "platform")
 * - namespace: Endpoint or operation identifier (e.g., "cart:add-seat")
 * - idempotencyKey: Client-provided unique key
 */
@Service
class IdempotencyService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val LOCK_PREFIX = "lock"
        private val RESULT_TTL: Duration = Duration.ofHours(24)
        private val LOCK_TTL: Duration = Duration.ofSeconds(30)
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 100L
    }

    /**
     * Execute supplier with idempotency protection.
     *
     * @param idempotencyKey Optional idempotency key from client
     * @param keyPrefix Module-specific prefix (e.g., "booking", "platform")
     * @param namespace Endpoint or operation identifier (e.g., "cart:add-seat")
     * @param responseType Expected response type for deserialization
     * @param supplier Function to execute if not cached
     * @return Result of supplier (either freshly computed or from cache)
     */
    fun <T : Any> withIdempotency(
        idempotencyKey: String?,
        keyPrefix: String,
        namespace: String,
        responseType: Class<T>,
        supplier: () -> T
    ): T {
        if (idempotencyKey.isNullOrBlank()) {
            return supplier()
        }

        val cacheKey = "$keyPrefix:$namespace:$idempotencyKey"
        val lockKey = "$keyPrefix:$LOCK_PREFIX:$namespace:$idempotencyKey"

        // Fast-path: return cached result if exists
        redisTemplate.opsForValue().get(cacheKey)?.let { cached ->
            logger.debug { "Idempotency cache hit: prefix=$keyPrefix namespace=$namespace key=$idempotencyKey" }
            return objectMapper.readValue(cached, responseType)
        }

        // Attempt to acquire exclusive lock with retries
        var attempt = 0
        while (attempt < MAX_RETRY_ATTEMPTS) {
            val lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "PROCESSING", LOCK_TTL) ?: false

            if (lockAcquired) {
                // Lock acquired - execute operation
                logger.debug { "Idempotency lock acquired: prefix=$keyPrefix namespace=$namespace key=$idempotencyKey" }

                try {
                    val result = supplier()

                    // Cache result for 24 hours
                    try {
                        val payload = objectMapper.writeValueAsString(result)
                        redisTemplate.opsForValue().set(cacheKey, payload, RESULT_TTL)
                        logger.debug { "Idempotency result cached: prefix=$keyPrefix namespace=$namespace key=$idempotencyKey" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to cache idempotent response for prefix=$keyPrefix namespace=$namespace" }
                    }

                    return result
                } finally {
                    // Release lock
                    redisTemplate.delete(lockKey)
                    logger.debug { "Idempotency lock released: prefix=$keyPrefix namespace=$namespace key=$idempotencyKey" }
                }
            }

            // Lock not acquired - another request is processing
            logger.debug { "Idempotency lock contention (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS): prefix=$keyPrefix namespace=$namespace key=$idempotencyKey" }

            // Exponential backoff
            val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl attempt)
            Thread.sleep(delayMs)

            // Check if result is now cached (other request completed)
            redisTemplate.opsForValue().get(cacheKey)?.let { cached ->
                logger.debug { "Idempotency cache hit after retry: prefix=$keyPrefix namespace=$namespace key=$idempotencyKey" }
                return objectMapper.readValue(cached, responseType)
            }

            attempt++
        }

        // All retries exhausted and no cached result - fail with conflict error
        logger.warn { "Idempotency lock timeout after $MAX_RETRY_ATTEMPTS attempts: prefix=$keyPrefix namespace=$namespace key=$idempotencyKey" }
        throw VenuesException.ResourceConflict(
            "Another request with the same idempotency key is being processed. Please retry in a moment."
        )
    }
}
