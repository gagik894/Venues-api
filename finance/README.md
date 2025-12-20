# Finance Module (Implementation)

This module implements the core financial logic and data persistence for the Venues API.
It is responsible for:
1.  **Merchant Persistence**: Storing and managing `MerchantProfile` entities.
2.  **Payment Routing**: Implementing the logic to decide *which* merchant key to use for a transaction.
3.  **Security**: Encrypting sensitive payment credentials in the database.

## Architecture

This module implements the `finance-api` (Port).
It uses **Hexagonal Architecture** principles:
- **Domain**: `MerchantProfile` (Aggregate Root).
- **Adapters**:
    - `MerchantProfileRepository` (Persistence Adapter).
    - `PaymentRoutingService` (Implementation of the Input Port).

## Key Features

### Payment Config Encryption
Sensitive data (API keys) in `MerchantProfile` is encrypted **at rest** using `PaymentConfigConverter`.
- **Encryption**: Happens automatically on `save()`.
- **Decryption**: Happens automatically on `find()`.
- **Security Policy**: If decryption fails (e.g., key mismatch or data corruption), the system throws a `Fatal Data Integrity` exception to prevent processing payments with invalid credentials.

### Financial Waterfall
The `PaymentRoutingService` resolves the merchant usage based on a hierarchy:
1.  **Event Override**: (Future) Specific events can have their own merchant.
2.  **Venue Override**: Venues can override the organization default.
3.  **Organization Default**: The fallback for all venues in an organization.

## Configuration
Requires `VenueConfigEncryptionService` to be available in the context (provided by `shared` module).
