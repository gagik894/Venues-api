# Booking Module Guide

This guide summarizes the booking module architecture, invariants, and primary flows. Follow the module boundaries: no other module should reach into booking internals, and booking only talks to other modules via their public APIs.

## Core Model
- **Cart**: Holds reserved seats/GA/tables for a session with an expiration (`expiresAt`). Items are priced at reservation time (snapshot pricing). Promo code is optional. Staff carts use longer TTLs.
- **Booking**: Created from a cart. Status is `PENDING` (awaiting payment) or `CONFIRMED` (payment complete) or `CANCELLED`. Stores snapshot pricing (unitPrice on items), service fee, discount, promo code, sales channel, and optional `externalOrderNumber` (payment reference).
- **BookingItem**: Seat, GA, or table line with the captured unit price and template name. Quantity > 1 only for GA.
- **Guest/User**: Either an authenticated user or a guest created on-the-fly. Guest preferredLanguage is used for emails.

## Expiration & Cleanup
- **Cart TTL**: Customer carts: 7 minutes initial, +5 minutes on activity (capped at 30 minutes). Staff carts: 20 minutes initial, +10 minutes on activity (capped at 30 minutes). Cleanup job (`CartCleanupService`) deletes expired carts and releases seats/GA/tables in batch.
- **Pending Booking TTL**: Pending bookings expire after ~15 minutes via `BookingCleanupService.expireOldPendingBookings()` (status -> CANCELLED, seats released).
- **Inventory**: Reservation APIs in the Event/Seating modules manage reserved/blocked seats and GA/table counts. Release happens on removal, cleanup, or cancellation. Finalization converts RESERVED to SOLD on booking confirmation.

## Flows
- **Cart build** (`CartService`, `CartQueryService` via `CartController`):
  - Add/remove seats (`/cart/seats`), GA (`/cart/ga`), tables (`/cart/table`), clear cart, apply promo code. Token carried via query or `cart_token` cookie.
  - Validation: checks session, pricing template, capacity, table booking mode, cart limits, duplicate items. Reservations are atomic per action.
- **Checkout** (`BookingController.checkout`):
  - Inputs: cart token, customer info (email/name/phone), optional promo code, payment reference. Auth is optional; userId attached if present.
  - Creates `Booking` in `PENDING` with snapshot prices. Redeems promo. Leaves inventory RESERVED. Returns booking + message.
- **Payment confirmation** (`/bookings/{id}/confirm`):
  - Confirms booking, finalizes inventory to SOLD, generates tickets, publishes `BookingConfirmedEvent` for email. Stores `externalOrderNumber` if provided. Uses userId when available for audit.
- **Cancellation** (`/bookings/{id}/cancel`):
  - Cancels booking, releases inventory, marks promo as released if applicable.
- **Email delivery** (`BookingConfirmationEmailService`):
  - Listens to `BookingConfirmedEvent`, fetches booking/session data, generates PDF tickets (always attached when >0 tickets), chooses venue SMTP when configured, falls back to global SMTP.

## Staff & Direct Sales
- **Staff Cart** (`StaffCartController`): Same cart ops but with staff TTL and venue permission checks. Checkout (`/staff/venues/{venueId}/cart/checkout`) converts to confirmed booking immediately (Direct Sale channel), finalizes inventory, generates tickets, and clears cart.
- **Direct Sale without cart** (`StaffBookingController`): `/direct-sales` takes explicit item codes, reserves them, builds a booking, confirms immediately, finalizes inventory, generates tickets, and publishes confirmation event.

## Reporting & Stats
- **Sales overview** (`SalesOverviewService`): revenue + ticket counts per session/event using EventApi stats plus booking sums.
- **Event stats** (`EventStatsService`): rich dashboards (platform, promo, day, zone/template, attendance) using booking statistics projections and seating metadata. Validates single currency per scope.
- **Venue reports** (`VenueReportsService`): aggregates orders/revenue/tickets by day and platform for a venue between dates; enforces single-currency reporting window.

## Promo Codes & Service Fees
- Promo codes validated via VenueApi. Applied at checkout or direct sale; redeemed on confirmation, released on cancel. Minimum order and max discount respected.
- Service fee percent from config (`app.booking.service-fee-percent`); applied to discounted subtotal; stored on booking.

## Integration Points
- **EventApi**: session info, price templates, reserve/release/block/unblock seats/GA/tables, ticket stats, table booking mode.
- **SeatingApi**: seat/GA/table metadata, lookup by code, seating chart structure.
- **TicketApi**: generate tickets on confirmation; fetch ticket data for emails.
- **UserApi**: resolve user email/name for confirmations.
- **VenueApi**: promo validation, venue name, SMTP config.

## Operational Notes
- Always use module APIs (no direct repository/entity access across modules).
- Snapshot pricing at reservation time; never read live template prices after cart creation for totals.
- Avoid `!!`; use safe handling and meaningful validation errors (`VenuesException`).
- Logs use KotlinLogging; do not log secrets or ticket QR payloads.

## Primary Endpoints
- Public cart: `/api/v1/cart/*` (add/remove seats/ga/tables, summary, clear, promo-code)
- Checkout: `POST /api/v1/checkout`
- Booking manage: `GET /api/v1/bookings/{id}`, `POST /api/v1/bookings/{id}/confirm`, `POST /api/v1/bookings/{id}/cancel`
- Staff cart: `/api/v1/staff/venues/{venueId}/cart/*` + `/checkout`
- Direct sale: `POST /api/v1/staff/venues/{venueId}/bookings/direct-sales`
- Staff queries/reports: `/api/v1/staff/venues/{venueId}/bookings` (with filters), `/sales/...`, `/reports`
