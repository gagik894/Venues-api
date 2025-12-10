# Location Module (Cities & Regions)

This module manages reference data for regions and cities, using stable codes/slugs to avoid exposing internal numeric
IDs. Intended for both public lookup and super-admin CRUD.

## Design principles

- Use codes/slugs only (no numeric IDs exposed).
- Multilingual names stored on entities; responses include localized `name` plus full `names` map where appropriate.
- Admin endpoints guarded by `SUPER_ADMIN` (JWT role).

## Key entities

- Region: `code` (ISO/gov code), `names` (json), `displayOrder`, `isActive`.
- City: `slug`, `names` (json), `region` (by code), `officialId` (optional), `displayOrder`, `isActive`.

## Public endpoints (no IDs leaked)

- `GET /api/v1/locations/cities` — all active cities (localized name, slug, region code/name).
- `GET /api/v1/locations/cities/{slug}` — city by slug.
- `GET /api/v1/locations/regions/{regionCode}/cities` — cities by region code.
- `GET /api/v1/locations/cities/search?q=&limit=&offset=` — search cities (paged).
- `GET /api/v1/locations/cities/compact` — lightweight list for dropdowns.

## Admin endpoints (SUPER_ADMIN)

- Regions:
    - `GET /api/v1/locations/admin/regions` — list regions (includes inactive).
    - `POST /api/v1/locations/admin/regions` — create region (CreateRegionRequest: code, names, displayOrder?).
    - `PUT /api/v1/locations/admin/regions/{code}` — update region (names/displayOrder/isActive).
- Cities:
    - `POST /api/v1/locations/admin/cities` — create city (CreateCityRequest: regionCode, slug, names, officialId?,
      displayOrder?).
    - `PUT /api/v1/locations/admin/cities/{slug}` — update city (UpdateCityRequest: regionCode?, names?, officialId?,
      displayOrder?, isActive?).

## DTO highlights

- RegionResponse: { code, names, name, displayOrder, isActive }
- CityResponse: { slug, names, name, region: { code, name }, officialId, displayOrder, isActive }
- CityCompact: { slug, name, regionName }
- CreateCityRequest / UpdateCityRequest: regionCode (string), not regionId.

## Notes

- City/region numeric IDs are no longer exposed in public/admin DTOs or endpoints.
- Internal persistence still uses IDs in the database, but all API surfaces use code/slug.

# Location Module - Reference Data Management

## Overview

The **Location** module provides government-quality reference data management for administrative regions and cities. It
serves as the foundational location infrastructure for the entire Venues API system.

## Purpose

- **Reference Data**: Manage static/semi-static location data (regions, cities)
- **Multilingual Support**: Store names in multiple languages (Armenian, English, Russian)
- **Interoperability**: Use ISO/government codes for integration with other systems
- **API Foundation**: Provide clean, cacheable endpoints for location selection

## Architecture

### Entities

#### Region (Administrative Region)

```kotlin
@Entity
@Table(name = "ref_regions")
class Region(
    var code: String,              // ISO code (e.g., "AM-ER", "AM-SH")
    var names: Map<String, String>,// {"hy": "Երևան", "en": "Yerevan"}
    var displayOrder: Int?,
    var isActive: Boolean = true
) : AbstractLongEntity()
```

**Examples**:

- AM-ER → Yerevan (capital city region)
- AM-SH → Shirak Province
- AM-LO → Lori Province

#### City (Community)

```kotlin
@Entity
@Table(name = "ref_cities")
class City(
    var region: Region,            // Parent region (mandatory)
    var slug: String,              // URL-friendly (e.g., "gyumri")
    var names: Map<String, String>,// {"hy": "Գյումրի", "en": "Gyumri"}
    var officialId: String?,       // Cadastre ID (optional)
    var displayOrder: Int?,
    var isActive: Boolean = true
) : AbstractLongEntity()
```

**Examples**:

- gyumri → Gyumri (Shirak)
- yerevan → Yerevan (Yerevan)
- dilijan → Dilijan (Tavush)

---

## API Endpoints

### Public Endpoints (No Authentication)

#### Regions

```http
# Get all active regions
GET /api/v1/locations/regions

# Get region by ID
GET /api/v1/locations/regions/{id}

# Get region by code
GET /api/v1/locations/regions/code/{code}
```

**Example Response**:

```json
{
  "success": true,
  "message": "Regions retrieved successfully",
  "data": [
    {
      "id": 1,
      "code": "AM-ER",
      "names": {
        "hy": "Երևան",
        "en": "Yerevan",
        "ru": "Ереван"
      },
      "displayOrder": 1,
      "isActive": true
    }
  ]
}
```

#### Cities

```http
# Get all active cities
GET /api/v1/locations/cities

# Get city by slug
GET /api/v1/locations/cities/{slug}

# Get cities by region
GET /api/v1/locations/regions/{regionId}/cities

# Search cities (multilingual)
GET /api/v1/locations/cities/search?q=gyumri

# Get compact city list (for dropdowns)
GET /api/v1/locations/cities/compact?lang=hy
```

**Example Response**:

```json
{
  "success": true,
  "message": "City retrieved successfully",
  "data": {
    "id": 2,
    "slug": "gyumri",
    "names": {
      "hy": "Գյումրի",
      "en": "Gyumri",
      "ru": "Гюмри"
    },
    "region": {
      "id": 8,
      "code": "AM-SH",
      "name": "Shirak"
    },
    "officialId": null,
    "displayOrder": 2,
    "isActive": true
  }
}
```

### Admin Endpoints (Require ADMIN Role)

```http
# Create region
POST /api/v1/locations/admin/regions
Authorization: Bearer {jwt-token}

# Update region
PUT /api/v1/locations/admin/regions/{id}
Authorization: Bearer {jwt-token}

# Create city
POST /api/v1/locations/admin/cities
Authorization: Bearer {jwt-token}

# Update city
PUT /api/v1/locations/admin/cities/{id}
Authorization: Bearer {jwt-token}
```

---

## Data Model

### Database Tables

```sql
-- Regions table
CREATE TABLE ref_regions
(
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(10) NOT NULL UNIQUE,
    names            JSONB       NOT NULL,
    display_order    INTEGER,
    is_active        BOOLEAN     NOT NULL DEFAULT true,
    created_at       TIMESTAMP   NOT NULL,
    last_modified_at TIMESTAMP   NOT NULL
);

-- Cities table
CREATE TABLE ref_cities
(
    id               BIGSERIAL PRIMARY KEY,
    region_id        BIGINT       NOT NULL REFERENCES ref_regions (id),
    slug             VARCHAR(100) NOT NULL UNIQUE,
    names            JSONB        NOT NULL,
    official_id      VARCHAR(50),
    display_order    INTEGER,
    is_active        BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMP    NOT NULL,
    last_modified_at TIMESTAMP    NOT NULL
);
```

### Indexes

- `idx_region_code` - Fast region lookup by ISO code
- `idx_city_slug` - Fast city lookup by slug (primary access)
- `idx_city_region` - Filter cities by region
- `idx_city_names_gin` - Multilingual name search (JSONB GIN index)

---

## Seed Data

Migration `V30__create_location_tables.sql` includes seed data for:

**Regions** (11 total):

- Yerevan (capital)
- 10 provinces (Aragatsotn, Ararat, Armavir, Gegharkunik, Kotayk, Lori, Shirak, Syunik, Tavush, Vayots Dzor)

**Cities** (8 initial):

- Yerevan (capital)
- Regional centers: Gyumri, Vanadzor, Gavar, Hrazdan
- Tourist destinations: Dilijan, Sevan, Echmiadzin

---

## Usage Examples

### In Venue Module

```kotlin
// Venue entity references city
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "city_id")
var city: City? = null
```

### In Event Module

```kotlin
// Event can reference city for location
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "city_id")
var city: City? = null
```

### In Frontend

```javascript
// Load regions for dropdown
const regions = await fetch('/api/v1/locations/regions');

// Load cities for selected region
const cities = await fetch(`/api/v1/locations/regions/${regionId}/cities`);

// Get city details
const city = await fetch('/api/v1/locations/cities/gyumri');
```

---

## Design Principles

### 1. Reference Data Pattern

- Table prefix `ref_` indicates static/lookup data
- Changes require deliberate admin action
- Rarely modified after initial setup

### 2. Multilingual by Default

- All names stored as JSONB: `{"hy": "...", "en": "...", "ru": "..."}`
- Minimum requirement: Armenian (hy) and English (en)
- Flexible for adding more languages without schema changes

### 3. URL-Friendly Slugs

- Cities use lowercase slugs for clean API URLs
- Example: `/api/v1/cities/gyumri` instead of `/api/v1/cities/2`
- Slugs are immutable (don't change after creation)

### 4. Soft Delete

- `isActive` flag instead of hard deletion
- Preserves referential integrity with venues, events, bookings
- Historical data remains accessible

### 5. ISO Compliance

- Region codes follow ISO 3166-2:AM standard
- Enables interoperability with government systems
- Examples: AM-ER, AM-SH, AM-LO

### 6. Optional Cadastre Integration

- Cities can link to official government IDs
- Supports integration with:
    - National Statistics Service
    - State Cadastre Committee
    - Ministry of Territorial Administration

---

## Caching Strategy

Reference data is ideal for caching:

```kotlin
// Suggested cache configuration (future enhancement)
@Cacheable("regions", unless = "#result == null")
fun getAllActiveRegions(): List<RegionResponse>

@Cacheable("cities", unless = "#result == null")
fun getAllActiveCities(): List<CityResponse>
```

**Recommendation**:

- Cache regions for 24 hours
- Cache cities for 12 hours
- Invalidate cache on admin updates

---

## Validation Rules

### Region Code

- Format: Uppercase alphanumeric with hyphens
- Pattern: `^[A-Z0-9-]+$`
- Length: 2-10 characters
- Example: "AM-ER", "AM-SH"

### City Slug

- Format: Lowercase alphanumeric with hyphens
- Pattern: `^[a-z0-9-]+$`
- Length: 2-100 characters
- Example: "gyumri", "yerevan", "dilijan"

### Multilingual Names

- Minimum 2 languages required
- Must include "hy" (Armenian)
- Must include "en" (English)
- Optional: "ru" (Russian), others

---

## Testing

### Unit Tests

```kotlin
@Test
fun `should find city by slug`() {
    val city = cityRepository.findBySlug("gyumri")
    assertNotNull(city)
    assertEquals("Gyumri", city.getName("en"))
}
```

### Integration Tests

```kotlin
@Test
fun `should return all active cities in region`() {
    val response = mockMvc.perform(
        get("/api/v1/locations/regions/8/cities")
    )
        .andExpect(status().isOk)
        .andReturn()

    // Verify response contains Gyumri (in Shirak)
}
```

---

## Dependencies

### Internal

- `common` - Exception handling, constants
- `shared` - Entity base classes, Spring Data auditing

### External

- Spring Boot Starter Data JPA
- PostgreSQL (JSONB support required)
- Hibernate (JPA implementation)
- Kotlin Logging

---

## Migration Guide

### Adding a New Region

```sql
INSERT INTO ref_regions (code, names, display_order, is_active)
VALUES ('AM-XX', '{"hy": "Armenian Name", "en": "English Name"}', 12, true);
```

### Adding a New City

```sql
INSERT INTO ref_cities (region_id, slug, names, display_order, is_active)
VALUES ((SELECT id FROM ref_regions WHERE code = 'AM-SH'),
        'new-city',
        '{"hy": "Հայերեն անուն", "en": "English Name"}',
        20,
        true);
```

### Deactivating a City (Soft Delete)

```sql
UPDATE ref_cities
SET is_active = false
WHERE slug = 'old-city';
```

---

## Performance Considerations

### Optimized Queries

- All common queries use indexed fields (code, slug, region_id)
- JSONB GIN index enables fast multilingual search
- Lazy loading for region relationship in cities

### Query Costs

- Region lookup by code: O(1) - unique index
- City lookup by slug: O(1) - unique index
- Cities by region: O(log n) - B-tree index
- Multilingual search: O(n) - full scan with JSONB filter

### Recommendations

- Cache public endpoints (regions/cities change rarely)
- Use compact representations for dropdowns
- Avoid loading region data when only city slug is needed

---

## Government Quality Standards

### ✅ SOLID Principles

- **Single Responsibility**: Each entity has one clear purpose
- **Open/Closed**: Easy to add new languages without schema changes
- **Dependency Inversion**: Controllers depend on service abstractions

### ✅ Clean Code

- Comprehensive documentation on all public methods
- Descriptive variable names (slug, officialId, displayOrder)
- Proper error handling with specific exception types

### ✅ Maintainability

- Clear separation of concerns (entity → repository → service → controller)
- Minimal duplication (DRY)
- Type-safe Kotlin code

### ✅ Security

- Admin endpoints protected with `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- Input validation on all request DTOs
- SQL injection prevention via parameterized queries

---

## Future Enhancements

### Potential Features

1. **Coordinates**: Add latitude/longitude for mapping
2. **Postal Codes**: Link cities to postal code ranges
3. **Population Data**: Store census data for analytics
4. **Translations API**: Endpoint to get all translations for a city
5. **History Tracking**: Audit log for admin changes
6. **Batch Import**: CSV/Excel import for bulk city data

### API Versioning

Consider creating `location-api` module if other modules need to depend on location types without circular dependencies.

---

## Support

For questions or issues related to the Location module:

- Review this README
- Check migration file: `V30__create_location_tables.sql`
- Examine entity documentation in source code

---

**Module Owner**: Government Cultural Department  
**Created**: November 19, 2025  
**Last Updated**: November 19, 2025

