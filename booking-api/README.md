# Booking API Module (`booking-api`)

## Overview
The `booking-api` module defines the **public contract** for the Booking domain. It contains the interfaces, DTOs (Data Transfer Objects), and events exposed to other modules in the system (e.g., `platform`, `legacy-api`, `reports`).

This module follows the **API Separation** pattern to ensure loose coupling. Consumers depend on `booking-api` but **never** on the implementation details in `booking`.

## Key Components

### 1. Booking API
**Interface:** `app.venues.booking.api.BookingApi`

The primary entry point for **write** operations (Commands).
- `createBookingFromCart`: Converts a reserved cart into a confirmed booking.
- `confirmBooking`: Finalizes a booking after payment.
- `cancelBooking`: Releases seats if payment fails or upon user request (pre-confirmation).
- `refundBooking`: Processes refunds for confirmed bookings.

### 2. Cart API
**Interface:** `app.venues.booking.api.CartApi`

Manages the shopping cart lifecycle (reservations).
- `createCart`: Initializes a new session.
- `addSeat` / `removeSeat`: Manages seat holds.
- `addGaItem` / `removeGaItem`: Manages general admission tickets.
- `getCart`: Retrieves current state.

### 3. Domain Events
**Package:** `app.venues.booking.event`

Events published by the booking domain for eventual consistency.
- `BookingCreatedEvent`
- `BookingConfirmedEvent`
- `BookingCancelledEvent`

## Data Transfer Objects (DTOs)

### BookingResponse
Standard response for most booking operations.
- `bookingId`: Unique UUID.
- `status`: `CONFIRMED`, `PENDING`, `CANCELLED`.
- `totalPrice`: Final amount charged.
- `items`: List of seats/tickets.
- `expiry`: When the reservation/pending state expires.

### CartDto
Represents the current shopping cart state.
- `token`: Session token for the cart.
- `items`: All reserved items.
- `pricing`: Subtotal, fees, taxes and total.
- `expiresAt`: Absolute timestamp when the cart is released.

## Usage Guide

### Dependency Injection
Consumers should inject the interface:
```kotlin
@Service
class PaymentListener(private val bookingApi: BookingApi) {
    fun onPaymentSuccess(event: PaymentSuccessEvent) {
        bookingApi.confirmBooking(event.bookingId, event.paymentId)
    }
}
```

### Error Handling
All API methods may throw subclasses of `VenuesException`:
- `VenuesException.ResourceNotFound`: ID not found.
- `VenuesException.ValidationFailure`: Invalid state transition or input.
- `VenuesException.Conflict`: Seat already taken (rare, mostly handled at Cart level).

## Testing
This module contains contracts. Implementation tests reside in the `booking` module.
However, contract tests or DTO serialization tests may exist here to ensure API stability.
