# Booking Module (`booking`)

## Overview
The `booking` module allows users to reserve seats, create carts, and finalize bookings. It implements the contracts defined in [booking-api](../booking-api/README.md).

## Architecture
This module follows **Domain-Driven Design (DDD)** principles.
- **Domain Layer**: Contains the core business logic (`Cart`, `Booking`, `BookingItem`, `SalesChannel`). Entities are rich with behavior (e.g., `cart.extendExpiration()`, `booking.confirm()`).
- **Service Layer**: Orchestrates domain objects and handles cross-cutting concerns (logging, transaction boundaries).
- **Persistence Layer**: Spring Data JPA repositories handling database interactions.
- **API Layer**: Controllers and message listeners (Input Ports).

## Key Domain Concepts

### 1. Shopping Cart (`Cart`)
The cart represents a temporary reservation.
- **Lifecycle**: Created -> Active -> Expired or Converted to Booking.
- **Expiration**:
    - **Customer Carts**: Short TTL (e.g., 20 mins max).
    - **Staff/Platform Carts**: Longer TTL or indefinite holds until release.
- **Security**: Carts can be bound to a `platformId` to prevent cross-channel hijacking.

### 2. Booking (`Booking`)
The immutable record of a sale (once confirmed).
- **State Machine**: `PENDING` -> `CONFIRMED` -> `CANCELLED` (or Refunded).
- **Invariants**:
    - A confirmed booking MUST have a valid `paymentId` or be a `DIRECT_SALE` with a `staffId`.
    - Service fees must be non-negative.
    - Prices are snapshotted at creation time to prevent race conditions with price updates.

## Business Rules
1.  **Platform Ownership**: API Clients (Platforms) cannot access each other's carts. This is enforced by `Cart.validatePlatformOwnership()`.
2.  **Concurrency**: Optimistic locking (`@Version`) is used on `Cart` and `Booking` to prevent double-spending or race conditions during high-traffic onsales.
3.  **Price Consistency**: `BookingCreationService` snapshots prices from the cart. Does not re-fetch current prices to ensure the user pays what they saw in the cart.

## Configuration
Configure these properties in `application.yml`:

| Property | Default | Description |
| :--- | :--- | :--- |
| `app.booking.service-fee-percent` | `0` | Percentage fee added to online sales (e.g., `2.5` for 2.5%). |
| `app.booking.cart-ttl-seconds` | `1200` | Time to live for a cart before items are released. |

## Troubleshooting

### "Cart has expired" Error
- **Cause**: User took too long to checkout.
- **Fix**: User must start over. Staff can extend cart expiration via `CartApi` if needed.

### "Seat does not belong to session"
- **Cause**: User tried to add a seat from Event A to a cart for Event B.
- **Fix**: UI Validation issue. Ensure frontend clears cart when switching events.

## Testing
This module is tested with:
- **Unit Tests**: Domain logic verification (`src/test/kotlin/app/venues/booking/domain/`).
- **Service Tests**: Mocked dependencies to test orchestration.
- **Integration Tests**: Full Spring Context tests involving H2 in-memory DB.

Run tests:
```bash
./gradlew :booking:test
```
