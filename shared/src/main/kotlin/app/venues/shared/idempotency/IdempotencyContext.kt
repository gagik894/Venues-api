package app.venues.shared.idempotency

/**
 * Immutable context for idempotency operations.
 *
 * Encapsulates all information needed for idempotency key generation
 * and caching, ensuring type safety and consistency across the application.
 *
 * Key Format: "{keyPrefix}:{operation}:{scopeId}:{idempotencyKey}"
 *
 * @property keyPrefix Module identifier (e.g., "booking", "platform", "payment")
 * @property operation Operation identifier (e.g., "cart:add-seat", "platform:hold")
 * @property scopeId Optional scope identifier for isolation (cart token, platform ID, etc.)
 * @property idempotencyKey Client-provided unique idempotency key
 * @property responseType Expected response type for deserialization and validation
 */
data class IdempotencyContext<T : Any>(
    val keyPrefix: String,
    val operation: String,
    val scopeId: String?,
    val idempotencyKey: String,
    val responseType: Class<T>
) {
    init {
        require(keyPrefix.isNotBlank()) { "Key prefix must not be blank" }
        require(operation.isNotBlank()) { "Operation must not be blank" }
        require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank" }
    }

    /**
     * Generate the cache key for storing the idempotent response.
     */
    fun getCacheKey(): String {
        return if (scopeId != null) {
            "$keyPrefix:$operation:$scopeId:$idempotencyKey"
        } else {
            "$keyPrefix:$operation:$idempotencyKey"
        }
    }

    /**
     * Generate the lock key for preventing concurrent execution.
     */
    fun getLockKey(): String {
        return "lock:${getCacheKey()}"
    }

    /**
     * Human-readable description for logging and debugging.
     */
    fun getDescription(): String {
        return if (scopeId != null) {
            "prefix=$keyPrefix operation=$operation scope=$scopeId key=$idempotencyKey"
        } else {
            "prefix=$keyPrefix operation=$operation key=$idempotencyKey"
        }
    }
}

