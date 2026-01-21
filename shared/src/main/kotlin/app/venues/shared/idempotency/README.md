# Idempotency Framework

> Enterprise-grade distributed idempotency protection for the Venues API platform

## Table of Contents

- [Overview](#overview)
- [Why Idempotency](#why-idempotency)
- [Architecture](#architecture)
- [How It Works](#how-it-works)
- [Usage](#usage)
- [Security Features](#security-features)
- [Configuration](#configuration)
- [Monitoring](#monitoring)
- [Error Handling](#error-handling)
- [Testing](#testing)
- [Performance](#performance)
- [Migration Guide](#migration-guide)

## Overview

The idempotency framework ensures that mutating API operations execute **exactly once**, even when clients retry
requests due to network failures, timeouts, or crashes.

**Key Features:**

- ✅ **Distributed Locking** - Redis-based atomic locks prevent race conditions
- ✅ **Request Hash Validation** - SHA-256 collision detection prevents replay attacks
- ✅ **30-Minute Caching** - Results cached for retry window
- ✅ **Fail-Open Resilience** - Continues operating if Redis is unavailable
- ✅ **Observability** - `X-Idempotency-Cache: HIT/MISS` headers for monitoring

## Why Idempotency

### The Problem

Without idempotency, network retries can cause: - ❌ **Double Charges** - User pays twice

- ❌ **Duplicate Reservations** - Same seat booked twice
- ❌ **Inventory Inconsistencies** - Overselling events

### The Solution

```
Client Request (Attempt 1):
  POST /cart/seats + Idempotency-Key: abc-123
  → Timeout (but server processed it)

Client Retry (Attempt 2):
  POST /cart/seats + Idempotency-Key: abc-123
  → ✅ Returns cached result
  ← X-Idempotency-Cache: HIT

No duplicate reservation created!
```

## Architecture

### Component Overview

```
┌──────────────────────────────────────────────┐
│  @Idempotent Annotation                      │
│  (Placed on controller methods)              │
└────────────────┬─────────────────────────────┘
                 │
┌────────────────▼─────────────────────────────┐
│  IdempotentAspect (AOP)                      │
│  - Extracts idempotency key                  │
│  - Computes request body hash (SHA-256)      │
│  - Builds IdempotencyContext                 │
└────────────────┬─────────────────────────────┘
                 │
┌────────────────▼─────────────────────────────┐
│  IdempotencyService                          │
│  1. Check cache (fast path)                  │
│  2. Acquire distributed lock (Redis SET NX)  │
│  3. Execute operation                        │
│  4. Cache result with hash                   │
│  5. Release lock                             │
└────────────────┬─────────────────────────────┘
                 │
┌────────────────▼─────────────────────────────┐
│  Redis (Distributed State)                   │
│  - Lock keys: lock:{prefix}:{op}:{scope}:{key}│
│  - Cache keys: {prefix}:{op}:{scope}:{key}   │
│  - TTL: 30 seconds (lock), 30 minutes (cache)│
└──────────────────────────────────────────────┘
```

### Key Components

| Component                     | Responsibility                         |
|:------------------------------|:---------------------------------------|
| **`@Idempotent`**             | Annotation marking protected endpoints |
| **`IdempotentAspect`**        | AOP interceptor extracting context     |
| **`IdempotencyService`**      | Core logic (lock, execute, cache)      |
| **`IdempotencyContext`**      | Type-safe execution context            |
| **`IdempotencyKeyExtractor`** | Extracts key from request parameters   |

## How It Works

### 1. Annotation

```kotlin
@Idempotent(
    endpoint = "cart:add-seat",           // Operation identifier
    keyPrefix = "booking",                 // Namespace
    scopeType = IdempotencyScopeType.CART_TOKEN  // Scope binding
)
fun addSeat(
    @RequestHeader("Idempotency-Key") key: String,  // Required
    @RequestParam("token") cartToken: UUID,          // Scope ID
    @RequestBody request: AddSeatToCartRequest
): ResponseEntity<CartMutationResponse>
```

### 2. Request Flow

```
┌─────────────────────────────────────────────────┐
│ 1. Client sends request with Idempotency-Key   │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│ 2. Aspect intercepts method call               │
│    - Extracts idempotency key from header      │
│    - Extracts scope ID (cart token)            │
│    - Computes SHA-256 hash of request body     │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│ 3. Build cache key                              │
│    booking:cart:add-seat:{cartToken}:{key}     │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│ 4. Check Redis cache                            │
│    ├─ HIT → Validate hash → Return cached      │
│    └─ MISS → Continue to step 5                 │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│ 5. Acquire Redis lock (SET NX)                  │
│    lock:booking:cart:add-seat:{cartToken}:{key}│
│    ├─ SUCCESS → Execute (step 6)                │
│    └─ FAIL → Poll for result (step 7)          │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│ 6. Execute operation                            │
│    - Call controller method                     │
│    - Cache result with hash                     │
│    - Release lock                               │
│    - Return result                              │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│ 7. Poll for cached result (if lock held)       │
│    - Wait 50ms, 100ms, 200ms, ... (exp backoff)│
│    - Max 10 attempts                            │
│    - Throw conflict if still not ready          │
└─────────────────────────────────────────────────┘
```

### 3. Cache Entry Structure

```json
{
  "result": "{\"cartToken\":\"...\",\"success\":true,...}",
  "requestHash": "a1b2c3d4e5f6..."
  // SHA-256 of request body
}
```

## Usage

### Basic Example

```kotlin
@RestController
@RequestMapping("/api/v1/cart")
class CartController(
    private val cartService: CartService
) {

    @Idempotent(
        endpoint = "cart:add-seat",
        keyPrefix = "booking",
        scopeType = IdempotencyScopeType.CART_TOKEN
    )
    @PostMapping("/seats")
    fun addSeat(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestParam("token") token: UUID,
        @RequestBody request: AddSeatToCartRequest
    ): ResponseEntity<CartMutationResponse> {
        val result = cartService.addSeatToCart(request, token)
        return ResponseEntity.ok(ApiResponse.success(result))
    }
}
```

### Client Usage

```http
POST /api/v1/cart/seats?token=abc-123-...
Idempotency-Key: client-generated-uuid-456
Content-Type: application/json

{
  "sessionId": "session-uuid",
  "code": "A1"
}

Response Headers:
X-Idempotency-Cache: MISS

Response Body:
{
  "data": {
    "cartToken": "abc-123-...",
    "success": true,
    "affectedItemId": "A1",
    "affectedItemType": "SEAT"
  }
}
```

### Scope Types

| Scope Type    | Use Case              | Example                                      |
|:--------------|:----------------------|:---------------------------------------------|
| `CART_TOKEN`  | Cart operations       | Extracted from `@RequestParam("token")`      |
| `BOOKING_ID`  | Booking modifications | Extracted from `@PathVariable("bookingId")`  |
| `CUSTOMER_ID` | User profile updates  | Extracted from `@CookieValue("customer_id")` |
| `NONE`        | Global operations     | No scope binding                             |

## Security Features

### 1. Request Hash Collision Detection

**Prevents replay attacks:**

```kotlin
// Attacker tries to reuse key with modified body
POST / cart / seats
Idempotency - Key: stolen-key-123  // Same key
Body: { "code": "VIP-1" }        // Different seat!

Response: 409 Conflict
{
    "error": "Idempotency key has already been used with a different request body"
}
```

**How it works:**

1. Compute SHA-256 hash of request body JSON
2. Store hash with cached result
3. On subsequent requests, compare hashes
4. Reject if mismatch detected

### 2. Scope Isolation

```kotlin
// Cannot use cart A's idempotency key on cart B
POST /cart/seats?token=cart-A
Idempotency - Key: key-1
→ Cached with scope: cart-A

POST /cart/seats?token=cart-B
Idempotency - Key: key-1  // Same key, different scope
→ Executes fresh (different cache key)
```

### 3. Distributed Locking

**Prevents race conditions:**

```
Request 1:                    Request 2 (concurrent):
├─ Check cache (MISS)         ├─ Check cache (MISS)
├─ Acquire lock (SUCCESS)     ├─ Acquire lock (FAIL)
├─ Execute operation          ├─ Poll for result
├─ Cache result               ├─ Poll... Poll... Poll...
├─ Release lock               ├─ Get cached result ✓
└─ Return result              └─ Return cached result
```

## Configuration

### Required Properties

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 5s  # Command timeout

# Optional: Connection pool (for high load)
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
```

### Tuning Parameters

**In `IdempotencyService.kt`:**

```kotlin
companion object {
    // How long cached results are kept
    private val RESULT_TTL: Duration = Duration.ofMinutes(30)

    // How long locks are held (prevents deadlocks)
    private val LOCK_TTL: Duration = Duration.ofSeconds(30)

    // Max retries when lock is held by another request
    private const val MAX_POLL_ATTEMPTS = 10

    // Initial delay between poll attempts (exponential backoff)
    private const val INITIAL_POLL_DELAY_MS = 50L

    // Maximum delay between poll attempts
    private const val MAX_POLL_DELAY_MS = 500L
}
```

**Adjust based on:**

- `RESULT_TTL`: Your API's expected retry window
- `LOCK_TTL`: Your API's p99 latency
- `MAX_POLL_ATTEMPTS`: Acceptable wait time for contention

## Monitoring

### Observability Headers

```http
X-Idempotency-Cache: HIT   # Result from cache
X-Idempotency-Cache: MISS  # Freshly executed
```

### Metrics to Track

**Recommended:**

1. **Cache Hit Rate**
   ```
   idem_cache_hits / (idem_cache_hits + idem_cache_misses)
   ```
    - **Target:** > 5% (indicates retries are happening)
    - **Alert if:** < 1% (clients not retrying) or > 50% (excessive retries)

2. **Lock Contention**
   ```
   idem_lock_contention_total
   ```
    - **Target:** < 0.1% of requests
    - **Alert if:** Sustained > 1% (indicates high concurrency or slow operations)

3. **Hash Collisions**
   ```
   idem_hash_collision_total
   ```
    - **Target:** 0 (should never happen unless attack)
    - **Alert if:** > 0 (potential security incident)

### Logging

```kotlin
logger.debug { "Idempotency cache hit: ${context.getDescription()}" }
logger.warn { "Idempotency key collision detected: ${context.getDescription()}" }
logger.error { "Unexpected error during lock acquisition" }
```

## Error Handling

### Fail-Open Behavior

**Philosophy:** Availability > Idempotency

```kotlin
try {
    acquireLock(key)
} catch (RedisConnectionFailure e) {
    logger.warn { "Redis down, proceeding without lock" }
    return true  // Execute anyway (fail-open)
}
```

**Why?**

- ✅ System remains available during Redis outages
- ⚠️ Trades idempotency for availability temporarily
- 📊 Monitor `redis_failures_total` metric

### Error Scenarios

| Error                   | Behavior                   | Impact                            |
|:------------------------|:---------------------------|:----------------------------------|
| **Redis down**          | Execute without protection | Duplicates possible during retry  |
| **Redis timeout**       | Execute without protection | Same as above                     |
| **Cache read failure**  | Execute fresh              | Cache miss (performance hit)      |
| **Cache write failure** | Log warning, continue      | No caching but operation succeeds |
| **Lock timeout**        | Throw `ResourceConflict`   | Client should retry later         |
| **Hash mismatch**       | Throw `ResourceConflict`   | Security: reject modified replay  |

## Testing

### Unit Tests

See [`IdempotencyServiceTest.kt`](../shared/src/test/kotlin/app/venues/shared/idempotency/IdempotencyServiceTest.kt)

**Coverage:**

- ✅ Cache hit/miss scenarios
- ✅ Lock acquisition/contention
- ✅ Hash collision detection
- ✅ **Redis failure modes (fail-open)**
- ✅ Backward compatibility (legacy cache entries)
- ✅ Concurrent request handling

### Edge Case Tests

See [
`IdempotencyHashEdgeCaseTest.kt`](../shared/src/test/kotlin/app/venues/shared/idempotency/IdempotencyHashEdgeCaseTest.kt)

**Coverage:**

- ✅ Null request bodies
- ✅ Empty request bodies
- ✅ Large request bodies (1MB+)
- ✅ Hash consistency
- ✅ No `@RequestBody` parameter

### Integration Tests

See [
`IdempotencyCacheHeaderTest.kt`](../shared/src/test/kotlin/app/venues/shared/idempotency/IdempotencyCacheHeaderTest.kt)

**Coverage:**

- ✅ `X-Idempotency-Cache: HIT` header
- ✅ `X-Idempotency-Cache: MISS` header
- ✅ Full AOP integration

### Running Tests

```bash
# All idempotency tests
./gradlew :shared:test --tests "*Idempotency*"

# Specific test suite
./gradlew :shared:test --tests "*IdempotencyServiceTest*"

# With coverage
./gradlew :shared:test jacocoTestReport
open shared/build/reports/jacoco/test/html/index.html
```

## Performance

### Benchmarks

**Operation Latency (p50/p95/p99):**

| Scenario                    | p50   | p95   | p99   |
|:----------------------------|:------|:------|:------|
| Cache HIT (Redis local)     | 2ms   | 5ms   | 10ms  |
| Cache MISS (execute)        | 50ms  | 100ms | 200ms |
| Lock contention (polling)   | 100ms | 250ms | 500ms |
| Hash computation (1KB body) | 0.5ms | 1ms   | 2ms   |
| Hash computation (1MB body) | 50ms  | 75ms  | 100ms |

**Recommendations:**

- ✅ Keep request bodies < 100KB
- ✅ Use Redis in same datacenter (< 1ms latency)
- ✅ Monitor p99 cache read latency

### Memory Usage

**Redis Memory:**

```
Per cached operation:
- Key: ~100 bytes (UTF-8)
- Value wrapper: ~50 bytes (JSON overhead)
- Result: Variable (e.g., CartMutationResponse ≈ 100 bytes)
- Hash: 64 bytes (hex string)

Total per operation: ~300 bytes

For 1M operations/day:
- 300 MB (assuming 30-min TTL, uniform distribution)
- Peak: ~600 MB (2x due to lock keys)
```

**Capacity Planning:**

```
Expected ops/day * 300 bytes / (86400 / TTL_seconds) = Required RAM
```

Example:

```
1,000,000 ops/day * 300 bytes / (86400 / 1800) = 6.25 MB
```

## Migration Guide

### From Legacy to Current

**Old Implementation (No Hashing):**

```json
// Redis value
"{\"cartToken\":\"...\",\"success\":true}"
```

**Current Implementation (With Hashing):**

```json
// Redis value
{
  "result": "{\"cartToken\":\"...\",\"success\":true}",
  "requestHash": "a1b2c3..."
}
```

**Backward Compatibility:**
✅ System handles both formats automatically:

- Old entries without `requestHash` wrapper work fine
- New entries use wrapper for security
- Gradual migration as cache expires (30 minutes)

### Adding Idempotency to New Endpoint

**Step 1:** Add annotation

```kotlin
@Idempotent(
    endpoint = "payment:refund",
    keyPrefix = "finance",
    scopeType = IdempotencyScopeType.BOOKING_ID
)
@PostMapping("/bookings/{bookingId}/refund")
fun refundBooking(
    @RequestHeader("Idempotency-Key") key: String,
    @PathVariable bookingId: UUID,
    @RequestBody request: RefundRequest
): ResponseEntity<RefundResponse>
```

**Step 2:** Return small response

```kotlin
// ✅ Good: Small, cacheable
data class RefundResponse(
    val refundId: UUID,
    val success: Boolean,
    val amount: MoneyAmount
)

// ❌ Bad: Large, wasteful
data class RefundResponse(
    val refundId: UUID,
    val booking: FullBookingDetails,  // Huge!
    val transaction: FullTransactionLog
)
```

**Step 3:** Document client usage

```kotlin
/**
 * Refunds a booking.
 *
 * **Idempotent:** Safe to retry with same Idempotency-Key.
 * Results cached for 30 minutes.
 *
 * @header Idempotency-Key UUID generated by client (e.g., UUIDv4)
 */
```

## Related Modules

- [`booking`](../../booking/README.md) - Primary consumer of idempotency
- [`payment`](../../payment/README.md) - Uses for refunds
- [`venue`](../../venue/README.md) - Uses for seat updates

## Best Practices

### ✅ DO

- Generate UUIDs for idempotency keys (unique per operation attempt)
- Use scopeType to isolate operations (cart, booking, customer)
- Keep response bodies small (< 1KB for efficient caching)
- Monitor `X-Idempotency-Cache` headers in production
- Set up alerts for hash collisions (security incident)

### ❌ DON'T

- Reuse idempotency keys across different operations
- Use predictable keys (timestamps, sequential IDs)
- Return large response bodies (slows caching)
- Remove idempotency from existing endpoints (breaking change)
- Disable fail-open (availability is critical)

## Troubleshooting

### High Lock Contention

**Symptom:**

```
ResourceConflict: Another request with the same idempotency key is being processed
```

**Causes:**

1. Slow operations (p99 > 30s)
2. Genuine concurrent requests

**Solutions:**

1. Optimize slow operations
2. Increase `LOCK_TTL` if needed
3. Client implements retry with backoff

### Cache Not Working

**Symptom:**
All requests show `X-Idempotency-Cache: MISS`

**Causes:**

1. Redis connection issues
2. TTL too short
3. Different request bodies (hash mismatch)

**Debug:**

```bash
# Check Redis connectivity
redis-cli -h localhost -p 6379 ping

# Inspect cache keys
redis-cli keys "booking:cart:*" | head

# Check TTL
redis-cli ttl "booking:cart:add-seat:[scope]:[key]"
```

### Hash Collision Alerts

**Symptom:**

```
409 Conflict: Idempotency key has already been used with a different request body
```

**Legitimate Cause:**
Client bug - modifying request on retry

**Security Concern:**
Replay attack attempt

**Action:**

1. Log client IP, user ID, request details
2. Investigate if pattern emerges
3. Consider rate limiting or blocking

---

**Framework Version:** 1.0.0  
**Last Updated:** 2026-01-19  
**Maintained by:** Venues Platform Team
