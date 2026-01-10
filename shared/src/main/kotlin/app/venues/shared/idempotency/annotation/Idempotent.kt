package app.venues.shared.idempotency.annotation

import app.venues.shared.idempotency.IdempotencyScopeType

/**
 * Marks a controller endpoint for automatic idempotency protection.
 *
 * **Purpose:**
 * Ensures that mutating operations execute exactly once, even when clients
 * retry requests due to network failures or timeouts. This is critical for
 * government-quality, production-grade systems.
 *
 * **Usage Example:**
 * ```kotlin
 * @PostMapping("/cart/seats")
 * @Idempotent(
 *     endpoint = "cart:add-seat",
 *     keyPrefix = "booking",
 *     scopeType = IdempotencyScopeType.CART_TOKEN
 * )
 * fun addSeatToCart(
 *     @RequestHeader("Idempotency-Key") idempotencyKey: String?,
 *     @RequestParam token: UUID?,
 *     @CookieValue("cart_token") cookieToken: UUID?,
 *     @RequestBody request: AddSeatRequest
 * ): ApiResponse<CartSummaryResponse>
 * ```
 *
 * **How it Works:**
 * 1. Aspect intercepts method execution
 * 2. Extracts Idempotency-Key from @RequestHeader("Idempotency-Key")
 * 3. Extracts scope identifier based on scopeType strategy
 * 4. Checks Redis cache for previous result
 * 5. If cached: returns immediately
 * 6. If not cached: executes method, caches result, returns
 *
 * **Key Format:** "{keyPrefix}:{endpoint}:{scopeId}:{idempotencyKey}"
 * Example: "booking:cart:add-seat:cart-uuid:idempotency-uuid"
 *
 * **Execution Order:**
 * Runs at @Order(1), before @Auditable aspect, ensuring idempotency
 * is checked first and duplicate requests don't create duplicate audit entries.
 *
 * @see app.venues.shared.idempotency.aspect.IdempotentAspect
 * @see app.venues.shared.idempotency.IdempotencyService
 * @see IdempotencyScopeType
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    /**
     * Endpoint identifier for operation namespacing.
     *
     * Should be unique within the module and descriptive of the operation.
     *
     * Examples:
     * - "cart:add-seat"
     * - "cart:remove-ga"
     * - "booking:confirm"
     * - "platform:hold"
     *
     * Conventions:
     * - Use lowercase with hyphens
     * - Use colons to separate logical groups
     * - Keep under 50 characters
     */
    val endpoint: String,

    /**
     * Key prefix for Redis keys.
     *
     * Must match the module making the request.
     *
     * Standard prefixes:
     * - "booking" - Booking/Cart operations
     * - "platform" - Platform API operations
     * - "payment" - Payment operations
     * - "staff" - Staff operations
     *
     * Note: Use consistent prefixes across your module to avoid key collisions.
     */
    val keyPrefix: String,

    /**
     * Scope type strategy for extracting scope identifiers.
     *
     * Determines how the aspect will extract the scope identifier
     * from method parameters. This provides isolation between different
     * entities (e.g., different carts, platforms, bookings).
     *
     * Standard strategies:
     * - CART_TOKEN - For cart operations (extracts from token param or cookie)
     * - PLATFORM_ID - For platform API (extracts from X-Platform-ID header)
     * - BOOKING_ID - For booking operations (extracts from path variable)
     * - CUSTOM - For custom parameter names (use customScopeName)
     * - NONE - No scoping (global idempotency key)
     *
     * @see IdempotencyScopeType
     */
    val scopeType: IdempotencyScopeType = IdempotencyScopeType.NONE,

    /**
     * Custom scope parameter name (used when scopeType = CUSTOM).
     *
     * Only applicable when scopeType is CUSTOM. Specifies the exact
     * parameter name to extract for scope isolation.
     *
     * Example:
     * ```kotlin
     * @Idempotent(
     *     endpoint = "venue:update",
     *     keyPrefix = "venue",
     *     scopeType = IdempotencyScopeType.CUSTOM,
     *     customScopeName = "venueId"
     * )
     * fun updateVenue(@PathVariable venueId: UUID, ...)
     * ```
     */
    val customScopeName: String = ""
)


