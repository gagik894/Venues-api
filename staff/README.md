# Staff Module

## Overview

The **Staff** module manages staff/admin accounts for the Venues API. It handles user authentication, authorization, and
account management for organization and venue administrators.

## Key Features

- **Multi-role support**: OWNER, ADMIN, STAFF, EVENT_MANAGER
- **Email verification workflow**: Async email verification with tokens
- **Security**: Failed login tracking, account locking, password hashing
- **Organization-based**: Staff accounts belong to organizations
- **Temporary access**: Event managers get time-limited access

## Domain Model

### Staff Entity

```kotlin
@Entity
class Staff(
    email: String,           // Unique login email
    passwordHash: String,    // BCrypt hash
    firstName: String?,
    lastName: String?,
    organization: Organization,  // Parent organization
    role: StaffRole = STAFF,
    status: StaffStatus = PENDING_EMAIL_VERIFICATION
)
```

### Enums

**StaffRole:**

- `OWNER` - Full organization access
- `ADMIN` - Administrative access to venues
- `STAFF` - Standard operational access
- `EVENT_MANAGER` - Temporary event-specific access

**StaffStatus:**

- `PENDING_EMAIL_VERIFICATION` - Email verification required
- `ACTIVE` - Can log in
- `INACTIVE` - Manually deactivated
- `SUSPENDED` - Suspended by system
- `DELETED` - Soft deleted

## API Endpoints

### Public Endpoints

```http
POST /api/v1/staff/auth/login          # Staff login
POST /api/v1/staff/auth/verify-email   # Verify email address
```

### Admin Endpoints (Organization OWNER only)

```http
POST /api/v1/admin/staff               # Create staff member
GET /api/v1/admin/staff                # List staff in organization
GET /api/v1/admin/staff/{id}           # Get staff by ID
PUT /api/v1/admin/staff/{id}           # Update staff info
POST /api/v1/admin/staff/{id}/activate    # Activate staff
POST /api/v1/admin/staff/{id}/deactivate  # Deactivate staff
DELETE /api/v1/admin/staff/{id}        # Delete staff (soft delete)
```

## Database Schema

**staff** table:

- `id` UUID - Primary key
- `email` VARCHAR(255) - Unique login email
- `password_hash` VARCHAR(255) - BCrypt hash
- `organization_id` UUID - Foreign key to organizations
- `role` VARCHAR(20) - Staff role
- `status` VARCHAR(20) - Account status
- `email_verified` BOOLEAN - Verification flag
- `verification_token` VARCHAR(255) - Email verification token
- `failed_login_attempts` INTEGER - Security tracking
- `account_locked_until` TIMESTAMP - Account lock timestamp
- `last_login_at` TIMESTAMP - Audit tracking

## Request/Response Examples

### Login Request

```json
{
  "email": "manager@opera.am",
  "password": "SecurePassword123"
}
```

### Login Response

```json
{
  "token": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "staff": {
    "id": "uuid",
    "email": "manager@opera.am",
    "firstName": "John",
    "lastName": "Manager",
    "role": "ADMIN",
    "status": "ACTIVE",
    "emailVerified": true
  }
}
```

### Create Staff Request

```json
{
  "email": "newstaff@opera.am",
  "password": "SecurePassword123",
  "firstName": "Jane",
  "lastName": "Staff",
  "phoneNumber": "+374123456789",
  "role": "STAFF"
}
```

## Security Features

### Password Security

- BCrypt hashing with cost factor 12
- Never stored as plain text
- Only hash transmitted/stored

### Account Locking

- Tracks failed login attempts
- Locks account after 5 failures
- 15-minute lockout period
- Automatically unlocks after period

### Email Verification

- Required for account activation
- Token-based verification (secure tokens)
- 24-hour token expiration
- Can resend verification email

## Best Practices

- ✅ Use UUIDs for all identifications
- ✅ Never expose staff IDs or tokens in URLs
- ✅ All sensitive operations require authentication
- ✅ Audit trail on all modifications
- ✅ Soft deletes for historical integrity
- ✅ Rate limiting on authentication endpoints

## Testing

```bash
# Test staff creation
POST /api/v1/admin/staff
{
  "email": "test@example.com",
  "password": "Test123456",
  "role": "STAFF"
}

# Test login
POST /api/v1/staff/auth/login
{
  "email": "test@example.com",
  "password": "Test123456"
}

# Test email verification (use token from email)
POST /api/v1/staff/auth/verify-email
{
  "token": "..."
}
```

## Module Dependencies

- `common` - Exception handling
- `shared` - Entity base classes
- `location` - City/region data
- `organization` - Organization entities

## Migration

**Migration V35**: Creates staff table with all necessary fields and indexes.

## Future Enhancements

- [ ] Two-factor authentication
- [ ] OAuth/SAML integration
- [ ] Session management
- [ ] Activity logging dashboard
- [ ] Bulk staff operations
- [ ] Staff templates/roles
- [ ] Department hierarchy

