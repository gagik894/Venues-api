# Organization Module

## Overview

The **Organization** module manages organizations that own and operate venues. Organizations are the top-level business
entities in the system, enabling multi-venue management and centralized administration.

## Key Features

- **Organizational hierarchy**: Organizations manage one or more venues
- **Multi-type support**: Government, non-profit, private, municipal, educational
- **Centralized management**: Single dashboard for all organization venues
- **Audit tracking**: Created/modified timestamps for all records
- **Soft deletes**: Preserve historical data integrity

## Domain Model

### Organization Entity

```kotlin
@Entity
class Organization(
    val name: String,
    val slug: String,              // URL-friendly identifier
    val type: OrganizationType,    // Business type
    val legalName: String?,        // Official legal name
    val taxId: String?,            // Tax identification
    val registrationNumber: String?,
    val address: String?,
    val city: City?,               // Reference to location
    val email: String?,
    val phoneNumber: String?,
    val logoUrl: String?,
    val isActive: Boolean = true
)
```

### OrganizationType Enum

- `GOVERNMENT` - Government ministry/department
- `NON_PROFIT` - Non-profit foundation
- `PRIVATE` - Private corporation
- `MUNICIPAL` - Municipal/city government
- `EDUCATIONAL` - University/school
- `OTHER` - Other organization type

### OrganizationVenueLink

Junction table linking organizations to venues with metadata:

- Tracks which venues belong to which organizations
- Soft delete support for historical integrity
- Timestamp tracking for audit

## API Endpoints

### Public Endpoints

```http
GET /api/v1/organizations              # List active organizations
GET /api/v1/organizations/{id}         # Get organization by ID
GET /api/v1/organizations/{slug}       # Get organization by slug (SEO-friendly)
```

### Admin Endpoints (Organization OWNER only)

```http
POST /api/v1/admin/organizations       # Create organization
GET /api/v1/admin/organizations        # List all organizations (admin)
GET /api/v1/admin/organizations/{id}   # Get organization by ID (admin)
PUT /api/v1/admin/organizations/{id}   # Update organization
DELETE /api/v1/admin/organizations/{id} # Delete organization (soft delete)

# Venue management
POST /api/v1/admin/organizations/{id}/venues           # Add venue
GET /api/v1/admin/organizations/{id}/venues           # List venues
DELETE /api/v1/admin/organizations/{id}/venues/{vid}  # Remove venue
```

## Database Schema

**organizations** table:

- `id` UUID - Primary key
- `slug` VARCHAR(100) - Unique URL-friendly identifier
- `name` VARCHAR(255) - Organization name
- `legalName` VARCHAR(255) - Official legal name
- `taxId` VARCHAR(50) - Tax identification
- `type` VARCHAR(20) - Organization type
- `address` VARCHAR(500) - Physical address
- `city_id` BIGINT - Foreign key to cities
- `email` VARCHAR(255) - Contact email
- `website` VARCHAR(500) - Organization website
- `is_active` BOOLEAN - Soft delete flag

**organization_venue_links** table:

- `id` BIGSERIAL - Primary key
- `organization_id` UUID - Foreign key
- `venue_id` UUID - Foreign key
- `is_active` BOOLEAN - Link status

## Request/Response Examples

### Create Organization Request

```json
{
  "name": "Armenian Opera House Foundation",
  "slug": "armenian-opera-foundation",
  "type": "NON_PROFIT",
  "legalName": "Armenian Opera House SNCO",
  "taxId": "0123456789",
  "cityId": 1,
  "email": "info@opera.am",
  "phoneNumber": "+37412345678",
  "website": "https://opera.am"
}
```

### Organization Response

```json
{
  "id": "uuid",
  "slug": "armenian-opera-foundation",
  "name": "Armenian Opera House Foundation",
  "type": "NON_PROFIT",
  "citySlug": "yerevan",
  "cityName": "Երևան",
  "phoneNumber": "+37412345678",
  "email": "info@opera.am",
  "website": "https://opera.am",
  "venueCount": 3,
  "createdAt": "2025-01-01T00:00:00Z"
}
```

### Add Venue Request

```json
{
  "venueId": "venue-uuid"
}
```

## Best Practices

- ✅ Use UUIDs for organization identification
- ✅ Use slugs for SEO-friendly URLs
- ✅ Validate tax IDs for government organizations
- ✅ Maintain audit trail on all changes
- ✅ Soft delete for historical integrity
- ✅ Organize venues logically under organizations

## Integration Points

### With Venues

- Organizations manage multiple venues
- Each venue belongs to one organization
- Venue permissions cascade from organization

### With Staff

- Staff members belong to organizations
- Organization OWNER manages staff
- Staff roles inherit organization hierarchy

### With Locations

- Organizations link to cities
- City-based organization filtering
- Regional organization reporting

## Queries and Use Cases

```bash
# Get all venues in an organization
GET /api/v1/admin/organizations/{id}/venues

# Search organizations by name
GET /api/v1/organizations?search=opera

# Filter organizations by type
GET /api/v1/organizations?type=NON_PROFIT

# Get organization statistics
GET /api/v1/admin/organizations/{id}/stats
```

## Module Dependencies

- `common` - Exception handling
- `shared` - Entity base classes
- `location` - City/region reference data
- `venue` - Venue entities

## Migrations

**Migration V34**: Creates organizations and organization_venue_links tables.

## Future Enhancements

- [ ] Organization templates
- [ ] Venue groups/networks within organizations
- [ ] Organization-level settings
- [ ] Bulk venue operations
- [ ] Organization analytics dashboard
- [ ] Department/branch hierarchy
- [ ] Partner organizations
- [ ] White-label support

## Security

- ✅ RBAC for organization access
- ✅ Data isolation between organizations
- ✅ Audit logging for all changes
- ✅ No exposure of internal IDs in public APIs
- ✅ Slug-based URLs for user-friendly links

