package app.venues.platform.security

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Service for managing nonce uniqueness using Redis.
 *
 * Provides distributed nonce validation to prevent replay attacks across
 * multiple application instances. Nonces are stored with TTL matching the
 * timestamp validation window (5 minutes + buffer).
 *
 * Redis Key Format: "platform:nonce:{platformId}:{nonce}"
 * Value: Timestamp when nonce was first used
 * TTL: 360 seconds (6 minutes - 5 min validation + 1 min buffer)
 */
@Service
class NonceService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val NONCE_KEY_PREFIX = "platform:nonce"
        private const val NONCE_TTL_SECONDS = 360L // 6 minutes (5 min validation window + 1 min buffer)
    }

    /**
     * Check if nonce has been used and mark it as used atomically.
     *
     * @param nonce The nonce to check
     * @param platformId The platform ID
     * @return true if nonce was already used (replay attack), false if nonce is new
     * @throws Exception if Redis is unavailable
     */
    fun isNonceUsed(nonce: String, platformId: Long): Boolean {
        val key = buildNonceKey(platformId, nonce)
        val timestamp = System.currentTimeMillis().toString()

        try {
            // Use SET NX (set if not exists) with TTL - atomic operation
            val wasSet = redisTemplate.opsForValue()
                .setIfAbsent(key, timestamp, Duration.ofSeconds(NONCE_TTL_SECONDS))

            // If wasSet is false, key already existed (nonce was used before)
            // If wasSet is true, we successfully set it (first time seeing this nonce)
            val isUsed = wasSet == false

            if (isUsed) {
                logger.warn { "Replay attack detected: nonce $nonce already used by platform $platformId" }
            } else {
                logger.debug { "Nonce $nonce marked as used for platform $platformId (TTL: ${NONCE_TTL_SECONDS}s)" }
            }

            return isUsed

        } catch (e: Exception) {
            // Redis unavailable - fail closed (reject request)
            logger.error(e) { "Redis error checking nonce: $nonce for platform $platformId" }
            throw NonceValidationException("Unable to validate nonce - cache unavailable", e)
        }
    }

    /**
     * Build Redis key for nonce storage.
     */
    private fun buildNonceKey(platformId: Long, nonce: String): String {
        return "$NONCE_KEY_PREFIX:$platformId:$nonce"
    }

    /**
     * Get nonce statistics (for monitoring/debugging).
     */
    fun getNonceStats(): NonceStats {
        return try {
            val pattern = "$NONCE_KEY_PREFIX:*"
            val keys = redisTemplate.keys(pattern)
            val totalNonces = keys.size

            // Count by platform (for monitoring)
            val byPlatform = keys.groupBy { key ->
                // Extract platform ID from key pattern: "platform:nonce:{platformId}:{nonce}"
                key.split(":").getOrNull(2)?.toLongOrNull()
            }.mapValues { it.value.size }

            NonceStats(
                totalActivenonces = totalNonces,
                noncesByPlatform = byPlatform.filterKeys { it != null }.mapKeys { it.key!! }
            )
        } catch (e: Exception) {
            logger.error(e) { "Error getting nonce statistics" }
            NonceStats(0, emptyMap())
        }
    }

    /**
     * Clear all nonces for a specific platform (admin operation).
     * Use with caution - only for emergency situations.
     */
    fun clearNoncesForPlatform(platformId: Long): Int {
        return try {
            val pattern = "$NONCE_KEY_PREFIX:$platformId:*"
            val keys = redisTemplate.keys(pattern)
            val count = keys.size

            if (count > 0) {
                redisTemplate.delete(keys)
                logger.warn { "Cleared $count nonces for platform $platformId" }
            }

            count
        } catch (e: Exception) {
            logger.error(e) { "Error clearing nonces for platform $platformId" }
            throw NonceValidationException("Unable to clear nonces", e)
        }
    }
}

/**
 * Nonce statistics for monitoring
 */
data class NonceStats(
    val totalActivenonces: Int,
    val noncesByPlatform: Map<Long, Int>
)

/**
 * Exception thrown when nonce validation fails due to infrastructure issues
 */
class NonceValidationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

