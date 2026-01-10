package app.venues.shared.idempotency.annotation

/**
 * Marks a controller endpoint for automatic idempotency protection.
 *
 * Usage:
 * @PostMapping("/cart/seats")
 * @Idempotent(endpoint = "cart:add-seat", namespaceKey = "cartToken")
 * fun addSeatToCart(...): ApiResponse<CartSummaryResponse> { ... }
 *
 * The aspect will:
 * 1. Extract Idempotency-Key from @RequestHeader("Idempotency-Key")
 * 2. Extract namespace value from method parameters (e.g., cartToken from @RequestParam or @CookieValue)
 * 3. Apply idempotency protection using the configured service
 * 4. Return cached result if available, otherwise execute and cache
 *
 * Key Format: "{keyPrefix}:{endpoint}:{namespaceValue}:{idempotencyKey}"
 * - keyPrefix: Module-specific (e.g., "booking", "platform")
 * - endpoint: Operation identifier (e.g., "cart:add-seat")
 * - namespaceValue: Scoped identifier (cart token, platform ID, etc.)
 * - idempotencyKey: Client-provided unique key
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    /**
     * Endpoint identifier for namespacing (e.g., "cart:add-seat", "platform:reserve").
     * Used to build the Redis key prefix.
     */
    val endpoint: String,

    /**
     * Parameter name to extract for namespace scoping.
     * 
     * Examples:
     * - "cartToken" - extracts from @RequestParam("token") or @CookieValue("cart_token")
     * - "platformId" - extracts from @RequestHeader("X-Platform-ID")
     * - null - no namespace scoping (uses endpoint only)
     */
    val namespaceKey: String? = null,

    /**
     * Key prefix for Redis keys (e.g., "booking", "platform").
     * Defaults to "booking" for backward compatibility.
     */
    val keyPrefix: String = "booking"
)
