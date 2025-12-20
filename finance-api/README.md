# Finance API Module

This module defines the public API, Data Transfer Objects (DTOs), and domain contracts for the Finance/Payment system of the Venues API.
It follows Clean Architecture principles, serving as the interface (Port) that other modules (like Venue or Order) use to interact with financial services.

## Key Components

### 1. `PaymentRoutingApi`
The main entry point for resolving financial destinations.
- **Role**: Input Port (Hexagonal Architecture).
- **Function**: Determines *who* receives money for a specific transaction (Organziation, Venue, or Event override).

### 2. `MerchantProfileDto`
Represents the financial identity of a payee.
- **Contains**: Legal entity info, Tax ID, and the sensitive `PaymentConfig`.
- **Security Warning**: The `config` field contains highly sensitive secrets (API keys). **NEVER expose this DTO to the frontend.**

### 3. `PaymentConfig`
A polymorphic configuration object holding credentials for various payment gateways:
- `Idram`
- `Telcel`
- `Arca`
- `Converse Bank`
- `Stripe`

## Security & usage

> [!WARNING]
> **Sensitive Data Handling**
> Objects in this module (specifically `PaymentConfig`) contain production credentials.
> - Do not log these objects directly without using their `toString()` methods (which are overridden to mask secrets).
> - Do not return `MerchantProfileDto` in REST API responses to the web/mobile client.
> - Ensure this module is only included by the `finance` implementation or other trusted backend modules (like `job-executor`).

## Validation
Configuration objects enforce validation upon instantiation:
- Required keys cannot be blank.
- Invalid configurations will throw `IllegalArgumentException`.
