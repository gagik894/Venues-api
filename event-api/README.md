# Event API Module

## Overview

The `event-api` module defines the public contract (Port) for the Event context. It adheres to the **Hexagonal Architecture** (Ports and Adapters) pattern, exposing interfaces and DTOs that other modules (like `booking`, `api-gateway`) can use without depending on the internal implementation details of the `event` module.

## Key Components

### 1. EventApi Interface
The core entry point is the `app.venues.event.api.EventApi` interface. It provides methods for:
- **Inventory Management**: Checking availability of seats, GA areas, and tables.
- **Reservations**: Atomically reserving and releasing resources.
- **Pricing**: Retrieving price templates and calculated prices for items.
- **Event Information**: Fetching event summaries and session details.

### 2. DTOs (Data Transfer Objects)
All data exchanged via `EventApi` is encapsulated in simple, immutable data classes located in `app.venues.event.api.dto`.

- **`EventSessionDto`**: Basic details about an event session.
- **`SessionInventoryResponse`**: A snapshot of the dynamic state (status, availability) of all seats, tables, and GA areas in a session. Designed for efficient merging with static seating chart structures.
- **`EventSummaryDto`**: A lightweight summary of an event, suitable for lists and cards.
- **`SessionTicketStatsDto`**: Statistics on ticket sales for a session.

## Usage

This module is a **compile-time dependency** for any module that needs to interact with events. The actual implementation is provided by the `event` module at runtime.

### Example: Reserving a Seat
```kotlin
// In a service class (e.g., BookingService)
class BookingService(private val eventApi: EventApi) {
    fun bookSeat(sessionId: UUID, seatId: Long) {
        try {
            val price = eventApi.reserveSeat(sessionId, seatId)
            // Proceed with payment...
        } catch (e: ResourceConflict) {
            // Handle unavailable seat
        }
    }
}
```

## Dependencies
This module should have minimal dependencies.
- `shared`: For common exceptions and utility types (e.g., `MoneyAmount`).
- Kotlin Standard Library.

## Rules
- **No Entities**: Do not expose JPA entities (`@Entity`) in this module.
- **No Logic**: DTOs should be dumb data carriers.
- **Stability**: Changes to this module affect all dependent consumers. Treat purely additive changes comfortably, but be cautious with breaking changes.
