Purpose

This file contains concise, project-specific instructions intended for GitHub Copilot (or similar AI-assisted coding
tools) to follow when generating code, comments, tests and migrations for the Venues API repository.

Principles (must follow)

- Government-quality: produce clear, secure, maintainable, well-tested code. Prefer clarity over cleverness.
- Single Responsibility & DRY: implementations should follow SOLID principles and avoid duplication.
- Modular-monolith boundaries: never cross module internals. Cross-module communication must happen through Ports (
  stable DTOs / interfaces). Do not reference another module's @Entity classes or repositories directly.
- Snapshot pricing: implement "snapshot at add-to-cart" pattern for booking flow (unitPrice stored in cart records at
  add time). Do not read live template price when rendering cart.
- Atomic DB operations: prefer single UPDATE/INSERT statements for concurrency-critical operations (seat reservation, GA
  capacity decrement). Avoid check-then-act race conditions.
- No unsafe Kotlin non-null assertions: avoid `!!`. Use safe handling or explicit validation with clear error messages.
- Use professional, concise comments only (Google-style). Explain why, not what.
- Do not leak secrets, credentials, or tokens into code or logs.

Coding conventions

- Language: Kotlin (use latest stable Kotlin version configured in Gradle). Use idiomatic Kotlin (README and gradle
  config determine exact version).
- Formatting: follow project's existing style. Keep imports stable and minimal.
- Nullability: validate inputs and throw meaningful VenuesException types (ValidationFailure, ResourceConflict,
  ResourceNotFound, etc.).
- Error messages: user-facing, human readable (example: "Validation failed for one or more fields"). Keep them concise.
- Logging: use KotlinLogging; logs should be structured and not contain secrets.

Project architecture rules

- Ports & Adapters: For each module, expose an interface in `app.<module>.port.api` (or `api` package). Implement the
  interface in the module's service. Other modules must inject the Port interface, not repositories/entities.
- DTOs only: Ports must use stable DTOs (no @Entity types) or primitive types. Keep DTOs immutable where practical.
- Database migrations: use Flyway. Name migrations `V{n}__short_description.sql`. Migration files must be idempotent and
  safe to run on dev and CI. When changing constraints or columns, create a new migration—never modify applied
  migrations.
- Migrations should include comments explaining intent and a small verification query in a comment block.
- Backfill carefully: when adding non-null columns to existing tables, add column nullable, backfill, then add NOT NULL
  constraint in separate steps.

Booking / Reservation rules

- Seat reservations: `session_seat_configs.status` is authoritative for seat availability. Use an atomic UPDATE to set
  AVAILABLE→RESERVED.
- GA reservations: use capacity and soldCount. Use atomic UPDATE to increment soldCount only when sufficient capacity
  exists.
- Cart snapshot: `CartSeat` and `CartItem` must contain `unitPrice` (BigDecimal/DECIMAL with scale 2) recorded at
  add-to-cart.
- Expiration: cleanup job deletes expired cart rows; when seats expire, release inventory (set
  session_seat_configs.status back to AVAILABLE) using an update.

Testing and validation

- Unit tests: write focused unit tests per vertical slice (controller -> service -> repository) using in-memory or test
  DB where appropriate.
- Integration tests: run Flyway migrations and run against a test Postgres instance in CI. Mock external modules via
  ports for service unit tests.
- Concurrency tests: add tests that assert atomic update behavior (two concurrent reservation attempts; one must fail).

Comments & documentation

- Keep comments brief and professional. One-sentence function javadoc for public API, with @throws for notable errors.
- Avoid over-commenting trivial logic.

Commit and PR guidance

- Commit messages: start with scoped prefix (e.g., `booking: add unit_price to CartItem`), short description, optional
  body.
- PRs: include summary of changes, migration steps (if any), testing instructions, and list of touched modules. Add
  reviewers from architecture and module owners.

Copilot prompt examples (recommended)

- Good: "Implement addSeatToCart in CartService using the SessionSeatConfigRepository.reserveSeatIfAvailable atomic
  update; snapshot price from SeatConfig.priceTemplate.price into CartSeat.unitPrice; throw
  VenuesException.ResourceConflict on 0 rows updated. Keep comments brief and Google-style."
- Good: "Create Flyway migration V15 to add unit_price to cart_tables: add column nullable, backfill, then set NOT NULL.
  Provide concise comments in SQL."
- Bad: "Just make seat reservation work; you can change anything." (Too broad; avoid.)

Do-not list

- Do not access another module's Entities or Repositories directly.
- Do not use `!!` or suppress null-safety lint rules.
- Do not modify applied Flyway migrations.
- Do not log secrets or print tokens in logs.

Maintenance

- Keep this file updated when architectural rules change.

Thank you. Follow these rules for any code-generation or completion inside this repository.
