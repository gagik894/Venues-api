# Booking Module

> Enterprise-grade cart and booking management for the Venues ticketing platform

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Key Features](#key-features)
- [Domain Model](#domain-model)
- [API Endpoints](#api-endpoints)
- [Cart System](#cart-system)
- [Idempotency Support](#idempotency-support)
- [Business Rules](#business-rules)
- [Configuration](#configuration)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

## Overview

The `booking` module is the core of the Venues ticketing system, responsible for:

- 🛒 Shopping cart management (seats, GA tickets, tables)
- 📝 Booking creation and confirmation
- 💰 Price calculation and service fees
- 🔒 **Idempotent cart operations** (prevents duplicate reservations)
- ⏱️ Expiration and hold management
- 🏷️ Multi-channel sales (online, staff, platform partners)

**Implements contracts from:** [`booking-api`](../booking-api/README.md)

## Architecture

The module follows **Domain-Driven Design (DDD)** and **Clean Architecture** principles:

```
┌─────────────────────────────────────────────┐
│  API Layer (Controllers)                    │
│  - CartController (idempotent)              │
│  - StaffCartController                      │
│  - BookingController                        │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│  Service Layer (Orchestration)              │
│  - CartService (mutation operations)        │
│  - CartQueryService (read operations)       │
│  - BookingCreationService                   │
│  - StaffCartService                         │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│  Domain Layer (Business Logic)              │
│  - Cart (aggregate root)                    │
│  - Booking (aggregate root)                 │
│  - CartItem, BookingItem                    │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│  Persistence Layer (Repositories)           │
│  - CartRepository                           │
│  - BookingRepository                        │
└─────────────────────────────────────────────┘
```

### Layer Responsibilities

**API Layer:**

- HTTP request handling
- Input validation
- **Idempotency protection** (via `@Idempotent` annotation)
- Response formatting

**Service Layer:**

- Transaction boundaries
- Cross-aggregate orchestration
- External API integration
- Logging and monitoring

**Domain Layer:**

- Business invariants enforcement
- State machine logic
- Rich domain behavior

**Persistence Layer:**

- Database operations
- Optimistic locking
- Lazy/eager loading strategies

## Key Features

### 🔄 Idempotent Cart Operations

All cart mutation operations (`addSeat`, `addGA`, `addTable`) are **idempotent**, preventing duplicate reservations:

```kotlin
@Idempotent(
    endpoint = "cart:add-seat",
    keyPrefix = "booking",
    scopeType = IdempotencyScopeType.CART_TOKEN
)
fun addSeat(@RequestBody request: AddSeatToCartRequest, ...): CartMutationResponse
```

**Benefits:**

- ✅ Network retries don't create duplicate bookings
- ✅ 30-minute result caching
- ✅ Request body hash collision detection
- ✅ `X-Idempotency-Cache: HIT/MISS` observability header

**See:** [Idempotency Documentation](../shared/src/main/kotlin/app/venues/shared/idempotency/README.md)

### 🚀 Performance Optimizations

**CartMutationResponse** (new):

- Small response (~100 bytes) for fast idempotent caching
- Clients fetch full cart state via separate `GET /cart/summary`
- Reduces Redis memory usage by 90%

**Old approach** (deprecated):

```kotlin
// ❌ Large response (~2KB) cached for every mutation
CartSummaryResponse(seats, gaItems, tables, prices, ...)
```

**New approach** (current):

```kotlin
// ✅ Minimal response (~100 bytes) cached
CartMutationResponse(cartToken, success, affectedItemId, affectedItemType)
// Client then calls GET /cart/summary for full state
```

### 🎯 Smart Cart Management

**Session Isolation:**

- Each event session has isolated cart tokens
- Cross-session contamination prevented

**Expiration Strategies:**

- **Customer carts:** 20 minutes (configurable)
- **Staff carts:** Extended/indefinite holds
- **Platform carts:** Custom TTL support

**Optimistic Locking:**

- Prevents double-spending during high-traffic onsales
- `@Version` field on Cart and Booking entities

## Domain Model

### Cart Aggregate

```kotlin
@Entity
class Cart(
    @Id val token: UUID,
    val sessionId: UUID,
    val eventId: UUID,
    val expiresAt: Instant,
    val isStaffCart: Boolean = false,
    val platformId: UUID? = null,
    @OneToMany val items: MutableList<CartItem>,
    @Version val version: Long = 0
) {
    // Rich domain behavior
    fun extendExpiration(duration: Duration)
    fun validatePlatformOwnership(platformId: UUID)
    fun isExpired(): Boolean
}
```

**States:**

- `ACTIVE` - Items reserved, can be modified
- `EXPIRED` - TTL reached, items released
- `CONVERTED` - Turned into a Booking (immutable)

### Booking Aggregate

```kotlin
@Entity
class Booking(
    @Id val id: UUID,
    val eventId: UUID,
    val sessionId: UUID,
    val customerId: UUID?,
    val status: BookingStatus,
    val paymentId: UUID?,
    val staffId: UUID?,
    @OneToMany val items: List<BookingItem>,
    @Version val version: Long = 0
) {
    fun confirm(paymentId: UUID)
    fun cancel(reason: String)
}
```

**State Machine:**

```
PENDING → CONFIRMED → CANCELLED
    ↓          ↓          ↓
 timeout   [final]    REFUNDED
```

## API Endpoints

### Cart Management

#### Add Seat to Cart

```http
POST /api/v1/cart/seats?token={cartToken}
Content-Type: application/json
Idempotency-Key: {unique-key}

{
  "sessionId": "uuid",
  "code": "A1"
}

Response: 200 OK
{
  "data": {
    "cartToken": "uuid",
    "success": true,
    "affectedItemId": "A1",
    "affectedItemType": "SEAT"
  }
}
```

#### Get Cart Summary

```http
GET /api/v1/cart/summary?token={cartToken}

Response: 200 OK
{
  "data": {
    "token": "uuid",
    "seats": [...],
    "gaItems": [...],
    "tables": [...],
    "totalPrice": {"amount": 10000, "currency": "USD"},
    "expiresAt": "2026-01-20T12:00:00Z"
  }
}
```

### Staff Operations

```http
POST /api/v1/staff/venues/{venueId}/cart/seats?token={cartToken}
Authorization: Bearer {staffToken}
```

## Cart System

### Cart Lifecycle

```
1. CREATE
   ↓
2. ADD ITEMS (idempotent)
   ↓
3. CHECKOUT
   ↓
4. CONFIRM BOOKING
   ↓
5. CART DELETED
```

### Expiration Handling

**Background Job:**

```kotlin
@Scheduled(fixedDelay = 60_000) // Every minute
fun releaseExpiredCarts() {
    val expired = cartRepository.findExpiredCarts(Instant.now())
    expired.forEach { cart ->
        reservationService.releaseReservations(cart.items)
        cartRepository.delete(cart)
    }
}
```

## Idempotency Support

Cart operations use the shared idempotency framework:

**Key Features:**

- ✅ SHA-256 request body hashing (collision detection)
- ✅ Redis-based distributed locking
- ✅ 30-minute result caching
- ✅ Fail-open resilience (continues if Redis down)

**Example:**

```kotlin
// First request
POST /cart/seats
Idempotency - Key: abc-123
→ Executes, caches result
← X - Idempotency - Cache: MISS

// Retry (same key, same body)
POST /cart/seats
Idempotency - Key: abc-123
→ Returns cached result
← X - Idempotency - Cache: HIT

// Replay attack (same key, different body)
POST /cart/seats
Idempotency - Key: abc-123
→ Detects hash mismatch
← 409 Conflict: "Key already used with different body"
```

## Business Rules

### 1. Platform Ownership

```kotlin
// API Clients cannot access each other's carts
cart.validatePlatformOwnership(requestPlatformId)
```

### 2. Session Binding

```kotlin
// Cannot add seat from Event A to cart for Event B
if (seat.sessionId != cart.sessionId) {
    throw BadRequest("Seat does not belong to session")
}
```

### 3. Price Snapshotting

```kotlin
// Booking locks prices from cart creation time
booking.items[0].unitPrice = cart.items[0].unitPrice
// NOT: seat.getCurrentPrice() - prevents price race conditions
```

### 4. Concurrent Modification

```kotlin
// Optimistic locking prevents lost updates
@Version val version: Long = 0
// If cart.version != DB version → OptimisticLockException
```

## Configuration

### Application Properties

```yaml
app:
  booking:
    # Service fee percentage (e.g., 2.5 for 2.5%)
    service-fee-percent: 0

    # Default cart TTL in seconds (20 minutes)
    cart-ttl-seconds: 1200

    # Max cart extension for staff (hours)
    max-staff-cart-extension-hours: 24

spring:
  data:
    redis:
      # Required for idempotency
      host: localhost
      port: 6379
      timeout: 5s
```

### Environment Variables

```bash
# Override in production
export APP_BOOKING_SERVICE_FEE_PERCENT=2.5
export APP_BOOKING_CART_TTL_SECONDS=900
export SPRING_DATA_REDIS_HOST=prod-redis.example.com
```

## Testing

### Test Coverage

- **Unit Tests:** Domain logic, validation
- **Service Tests:** Business rules, orchestration
- **Controller Tests:** API contracts, idempotency
- **Integration Tests:** Full Spring context + H2 database

### Running Tests

```bash
# All tests
./gradlew :booking:test

# Specific test class
./gradlew :booking:test --tests "*CartControllerTest*"

# With coverage
./gradlew :booking:test jacocoTestReport
```

### Test Categories

**CartControllerTest:**

- ✅ Seat, GA, Table mutations return `CartMutationResponse`
- ✅ Response structure validation
- ✅ Idempotency headers

**CartServiceTest:**

- ✅ Expiration logic
- ✅ Platform ownership validation
- ✅ Price calculation

**IdempotencyServiceTest (shared):**

- ✅ Hash collision detection
- ✅ Redis failure modes (fail-open)
- ✅ Backward compatibility

## Troubleshooting

### Cart Expired Error

**Symptom:**

```json
{
  "error": "Cart has expired",
  "code": "CART_EXPIRED"
}
```

**Causes:**

1. User took > `cart-ttl-seconds` to checkout
2. Background job released items

**Solutions:**

- Extend TTL in config
- Staff can extend via `PATCH /staff/cart/extend`
- User must create new cart

### Idempotency Key Conflict

**Symptom:**

```json
{
  "error": "Idempotency key has already been used with a different request body",
  "code": "RESOURCE_CONFLICT"
}
```

**Cause:**
Client retried with same idempotency key but different request body (potential attack or bug)

**Solution:**

- Generate new idempotency key
- Verify client isn't modifying request body on retry

### Optimistic Lock Exception

**Symptom:**

```
OptimisticLockException: Row was updated or deleted by another transaction
```

**Cause:**
High concurrency - two requests modified same cart simultaneously

**Solution:**

- Client should retry with exponential backoff
- Indicates healthy contention handling

### Redis Connection Timeout

**Symptom:**

```
RedisCommandTimeoutException: Command timed out after 5 seconds
```

**Impact:**

- Idempotency protection disabled (fail-open)
- Operations continue executing
- May allow duplicates during retry

**Solutions:**

1. Check Redis health: `redis-cli ping`
2. Scale Redis if overloaded
3. Increase timeout: `spring.data.redis.timeout=10s`

## Related Modules

- [`booking-api`](../booking-api/README.md) - API contracts
- [`shared`](../shared/README.md) - Idempotency framework
- [`seating`](../seating/README.md) - Seat reservation
- [`event`](../event/README.md) - Event and session management

## Migration Notes

### From CartSummaryResponse to CartMutationResponse

**Before (deprecated):**

```kotlin
val response: CartSummaryResponse = cartService.addSeat(...)
// Response includes full cart state (~2KB)
```

**After (current):**

```kotlin
val mutation: CartMutationResponse = cartService.addSeat(...)
// Minimal response (~100 bytes)

// Fetch full state separately if needed
val summary: CartSummaryResponse = cartQueryService.getCartSummary(mutation.cartToken)
```

**Why?**

- 🚀 90% reduction in idempotency cache size
- 🎯 Better separation of concerns (CQRS pattern)
- ✅ Faster response times

---

**Module Version:** 1.0.0  
**Last Updated:** 2026-01-19  
**Maintained by:** Venues Engineering Team
