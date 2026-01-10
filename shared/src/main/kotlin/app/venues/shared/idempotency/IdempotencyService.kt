package app.venues.shared.idempotency

import app.venues.common.exception.VenuesException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Enterprise-grade idempotency service for protecting API endpoints from duplicate execution.
 *
 * **Purpose:**
 * Ensures that mutating operations (POST/PUT/PATCH/DELETE) execute exactly once,
 * even when clients retry requests due to network failures or timeouts.
 *
 * **Security & Atomicity:**
 * Uses atomic Redis SET NX (set if not exists) to acquire exclusive locks,
 * preventing race conditions where concurrent requests with the same
 * idempotency key could both execute before the cache is populated.
 *
 * **Algorithm:**
 * 1. Fast-path: Check if result is already cached → return immediately
 * 2. Acquire exclusive lock using atomic SET NX with TTL
 * 3. If lock acquired: execute operation, cache result, release lock
 * 4. If lock not acquired: poll for cached result with exponential backoff
 * 5. After max retries: fail with conflict error (operation still processing)
 *
 * **Failure Modes:**
 * - Redis unavailable → execute without protection (fail-open for availability)
 * - Serialization failure → log warning, continue (don't block operation)
 * - Lock timeout → ResourceConflict (client should retry with same key)
 *
 * **Key Format:** Managed by IdempotencyContext
 * Example: "booking:cart:add-seat:cart-token-uuid:idempotency-key-uuid"
 *
 * @see IdempotencyContext
 */
@Service
class IdempotencyService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private val RESULT_TTL: Duration = Duration.ofHours(24)
        private val LOCK_TTL: Duration = Duration.ofSeconds(30)
        private const val MAX_POLL_ATTEMPTS = 10
        private const val INITIAL_POLL_DELAY_MS = 50L
        private const val MAX_POLL_DELAY_MS = 500L
    }

    /**
     * Execute supplier with idempotency protection using strongly-typed context.
     *
     * **Primary entry point for idempotency-protected operations.**
     *
     * @param context Idempotency context containing all required information
     * @param supplier Function to execute if not cached
     * @return Result of supplier (either freshly computed or from cache)
     * @throws VenuesException.ResourceConflict if lock timeout occurs
     */
    fun <T : Any> executeWithIdempotency(
        context: IdempotencyContext<T>,
        supplier: () -> T
    ): T {
        val cacheKey = context.getCacheKey()
        val lockKey = context.getLockKey()

        // Fast-path: Check for cached result
        getCachedResult(cacheKey, context)?.let { cached ->
            logger.debug { "Idempotency cache hit: ${context.getDescription()}" }
            return cached
        }

        // Attempt to acquire exclusive lock
        val lockAcquired = tryAcquireLock(lockKey)

        if (lockAcquired) {
            return executeLocked(context, cacheKey, lockKey, supplier)
        } else {
            return pollForResult(context, cacheKey)
        }
    }

    /**
     * Execute operation with lock acquired.
     */
    private fun <T : Any> executeLocked(
        context: IdempotencyContext<T>,
        cacheKey: String,
        lockKey: String,
        supplier: () -> T
    ): T {
        logger.debug { "Idempotency lock acquired: ${context.getDescription()}" }

        try {
            val result = supplier()

            // Cache result for future requests
            cacheResult(cacheKey, result, context)

            return result
        } finally {
            releaseLock(lockKey)
            logger.debug { "Idempotency lock released: ${context.getDescription()}" }
        }
    }

    /**
     * Poll for cached result when lock is held by another request.
     */
    private fun <T : Any> pollForResult(
        context: IdempotencyContext<T>,
        cacheKey: String
    ): T {
        logger.debug { "Idempotency lock contention, polling for result: ${context.getDescription()}" }

        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            // Exponential backoff with max delay cap
            val delayMs = minOf(
                INITIAL_POLL_DELAY_MS * (1 shl attempt),
                MAX_POLL_DELAY_MS
            )
            TimeUnit.MILLISECONDS.sleep(delayMs)

            // Check if result is now available
            getCachedResult(cacheKey, context)?.let { cached ->
                logger.debug { "Idempotency cache hit after polling (attempt ${attempt + 1}): ${context.getDescription()}" }
                return cached
            }
        }

        // All polling attempts exhausted - operation still in progress
        logger.warn { "Idempotency lock timeout after $MAX_POLL_ATTEMPTS polling attempts: ${context.getDescription()}" }
        throw VenuesException.ResourceConflict(
            "Another request with the same idempotency key is being processed. Please retry in a moment."
        )
    }

    /**
     * Attempt to acquire exclusive lock atomically.
     */
    private fun tryAcquireLock(lockKey: String): Boolean {
        return try {
            redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", LOCK_TTL) ?: false
        } catch (e: RedisConnectionFailureException) {
            logger.warn(e) { "Redis connection failed during lock acquisition, proceeding without lock" }
            true // Fail-open: allow operation to proceed
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during lock acquisition, proceeding without lock" }
            true // Fail-open for availability
        }
    }

    /**
     * Release lock after operation completion.
     */
    private fun releaseLock(lockKey: String) {
        try {
            redisTemplate.delete(lockKey)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to release lock: $lockKey (will expire automatically)" }
            // Non-critical: lock will expire automatically due to TTL
        }
    }

    /**
     * Retrieve cached result from Redis.
     */
    private fun <T : Any> getCachedResult(
        cacheKey: String,
        context: IdempotencyContext<T>
    ): T? {
        return try {
            redisTemplate.opsForValue().get(cacheKey)?.let { json ->
                deserializeResult(json, context)
            }
        } catch (e: RedisConnectionFailureException) {
            logger.warn(e) { "Redis connection failed during cache read" }
            null
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error reading from cache: $cacheKey" }
            null
        }
    }

    /**
     * Cache operation result in Redis.
     */
    private fun <T : Any> cacheResult(
        cacheKey: String,
        result: T,
        context: IdempotencyContext<T>
    ) {
        try {
            val json = objectMapper.writeValueAsString(result)
            redisTemplate.opsForValue().set(cacheKey, json, RESULT_TTL)
            logger.debug { "Idempotency result cached: ${context.getDescription()}" }
        } catch (e: JsonProcessingException) {
            logger.warn(e) { "Failed to serialize result for caching: ${context.getDescription()}" }
            // Non-critical: operation succeeded, just not cached
        } catch (e: RedisConnectionFailureException) {
            logger.warn(e) { "Redis connection failed during cache write" }
            // Non-critical: operation succeeded, just not cached
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error caching result: ${context.getDescription()}" }
            // Non-critical: don't fail the operation due to cache failure
        }
    }

    /**
     * Deserialize cached JSON to response object.
     */
    private fun <T : Any> deserializeResult(
        json: String,
        context: IdempotencyContext<T>
    ): T {
        return try {
            objectMapper.readValue(json, context.responseType)
        } catch (e: JsonProcessingException) {
            logger.error(e) { "Failed to deserialize cached result: ${context.getDescription()}" }
            throw VenuesException.InternalError(
                "Failed to deserialize cached idempotent response. Please retry with a new idempotency key."
            )
        }
    }
}
