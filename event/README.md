# Event Module

## Overview

The `event` module implements the core event management business logic. It follows **Hexagonal Architecture**, providing the implementation for the `EventApi` port defined in the `event-api` module.

## Architecture

- **Domain Layer**: Contains rich entities (`Event`, `EventSession`, `EventPriceTemplate`) that encapsulate state transitions and invariants.
- **Service Layer**: Orchestrates business flows, enforcing transactional boundaries and interacting with other modules via their APIs (`VenueApi`, `SeatingApi`).
- **Repository Layer**: Handles data persistence using Spring Data JPA.
- **API (Adapter) Layer**: Exposes REST endpoints for the frontend.

## Data Model

### Event
The root aggregate. Holds high-level information (title, description, basic venue info).
- **Status Workflow**: `DRAFT` -> `PUBLISHED` -> `SUSPENDED` / `ARCHIVED` -> `DELETED`.
- **Relationship**: One-to-Many with `EventSession` and `EventPriceTemplate`.

### EventSession
Represents a specific time slot for an event.
- **Status Workflow**: `ON_SALE` -> `PAUSED` / `SOLD_OUT` -> `SALES_CLOSED` -> `CANCELLED`.
- **Inventory**: Each session has its own independent inventory state (pricing, availability).

## Key Services

### `EventService`
The main entry point for CRUD operations on Events and Sessions.
- **Responsibility**: Event creation, validation, status management, and search.
- **Auditing**: Records critical actions via `AuditActionRecorder`.

### `EventSeatingService` (Star Service)
Manages the inventory configuration (Seats, Tables, GA) for sessions.
- **Sparse Matrix Pattern**: To optimize database usage, we **do not** create database rows for every single seat in every session.
    - If a seat is available and uses the default price, no row exists in `session_seat_configs`.
    - Rows are created only when a seat is:
        - Reserved/Sold
        - Blocked
        - Has a price override different from the default
    - Implementation: `SeatConfigSparseService` and `EventSeatingService`.

### `EventPricingService`
Handles dynamic pricing logic.
- **Aggregated View**: Provides a unified pricing view across multiple sessions. If sessions have different prices for the same seat, it flags them as "Mixed".

## Testing Strategy
- **Unit Tests**: Use `MockK` for mocking dependencies. Focus on state transitions and service logic.
- **Integration Tests**: Use `@SpringBootTest` with Testcontainers (Postgres) to verify repository queries and end-to-end flows.

## Dependencies
- **Internal**: `event-api`, `venue-api`, `seating-api`, `staff-api`, `media-api`, `booking-api`.
- **External**: Spring Boot Data JPA, Web, Security, Redis.
