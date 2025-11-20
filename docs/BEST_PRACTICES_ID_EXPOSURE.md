# Best Practices for ID Exposure in Public APIs

## Summary of Changes

You were **100% correct** to call this out! I made a mistake by using slugs for CRUD operations on venues. Here's the
corrected approach:

---

## ✅ Correct Best Practices

### 1. **User-Generated Content → Use UUIDs**

- Venues are created by users/admins
- **Always use UUIDs** for CRUD operations
- UUIDs are stable, unique, and don't expose business logic

**Example:**

```http
GET /api/v1/venues/{uuid}
PUT /api/v1/admin/venues/{uuid}
DELETE /api/v1/admin/venues/{uuid}
```

### 2. **Reference Data → Use Codes/Slugs**

- Categories, Cities, Regions are admin-controlled reference data
- **Use semantic codes/slugs** for filtering and reference

**Example:**

```http
GET /api/v1/venues/category/OPERA
GET /api/v1/venues/city/yerevan
GET /api/v1/venues/region/AM-ER
```

### 3. **Slugs as Supplementary (SEO-Friendly)**

- Provide slug-based endpoint **in addition to** UUID
- Used for user-friendly URLs and SEO
- **Never use for CRUD operations**

**Example:**

```http
# Primary (UUID-based)
GET /api/v1/venues/550e8400-e29b-41d4-a716-446655440000

# Supplementary (slug-based, SEO-friendly)
GET /api/v1/venues/slug/armenian-opera
```

---

## Why This Matters

### ❌ Wrong Approach (My Initial Mistake)

```kotlin
// Using slug for CRUD - BAD!
fun updateVenue(slug: String, request: UpdateVenueRequest)
fun deleteVenue(slug: String)
```

**Problems:**

1. Slugs can change (user might want to update URL)
2. Breaks existing references if slug changes
3. URL encoding issues with special characters
4. Not stable for API contracts

### ✅ Correct Approach

```kotlin
// Using UUID for CRUD - GOOD!
fun updateVenue(id: UUID, request: UpdateVenueRequest)
fun deleteVenue(id: UUID)

// Slug only for read-only SEO access
fun getVenueBySlug(slug: String): VenueDetailResponse
```

**Benefits:**

1. Stable identifier that never changes
2. No URL encoding issues
3. Standard REST best practice
4. API contracts remain stable

---

## API Structure (Corrected)

### Public Endpoints

```http
# List all venues
GET /api/v1/venues

# Get venue by UUID (primary)
GET /api/v1/venues/{uuid}

# Get venue by slug (SEO-friendly alternative)
GET /api/v1/venues/slug/{slug}

# Search
GET /api/v1/venues/search?q=opera

# Filter by reference data (uses codes/slugs)
GET /api/v1/venues/category/OPERA        # categoryCode
GET /api/v1/venues/city/yerevan          # citySlug
GET /api/v1/venues/region/AM-ER          # regionCode
```

### Admin Endpoints (All Use UUIDs)

```http
# Create venue
POST /api/v1/admin/venues

# Update venue (UUID required)
PUT /api/v1/admin/venues/{uuid}

# Delete venue (UUID required)
DELETE /api/v1/admin/venues/{uuid}

# Activate/Suspend (UUID required)
POST /api/v1/admin/venues/{uuid}/activate
POST /api/v1/admin/venues/{uuid}/suspend
```

---

## Response Format

### Public Response (Includes Both UUID and Slug)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "slug": "armenian-opera",
  "name": "Armenian Opera",
  
  "citySlug": "yerevan",
  "cityName": "Երևան",
  
  "categoryCode": "OPERA",
  "categoryName": "Օպերա",
  "categoryColor": "#E91E63"
}
```

Frontend can use:

- **UUID** for API operations (update, delete, etc.)
- **Slug** for user-friendly URLs (`/venues/armenian-opera`)

---

## categoryCode Explanation

### Why "categoryCode"?

- Categories are **reference data** (like cities, regions)
- Admins define them once, users select from list
- Using **codes** (not IDs) makes APIs more semantic

**Example:**

```json
{
  "code": "OPERA",
  "names": {
    "hy": "Օպերա",
    "en": "Opera",
    "ru": "Опера"
  },
  "color": "#E91E63",
  "icon": "music-note"
}
```

**API Usage:**

```http
# Filter venues by category code
GET /api/v1/venues/category/OPERA

# Create venue with category
POST /api/v1/admin/venues
{
  "name": "My Opera",
  "categoryCode": "OPERA"  ← Uses code, not ID
}
```

**Benefits:**

1. **Semantic**: "OPERA" is more meaningful than "3"
2. **Stable**: Code doesn't change (unlike incremental IDs)
3. **Language-agnostic**: Same code across all languages
4. **No exposure of internal IDs**

---

## Summary of Rules

| Data Type      | Identifier | Usage                  | Example                                |
|----------------|------------|------------------------|----------------------------------------|
| **Venues**     | UUID       | All CRUD operations    | `550e8400-e29b-41d4-a716-446655440000` |
| **Venues**     | slug       | SEO-friendly read-only | `armenian-opera`                       |
| **Categories** | code       | Filtering, selection   | `OPERA`, `MUSEUM`                      |
| **Cities**     | slug       | Filtering              | `yerevan`, `gyumri`                    |
| **Regions**    | code       | Filtering              | `AM-ER`, `AM-SH`                       |

---

## What Was Fixed

### Service Layer

- ✅ `getVenue(id: UUID)` - Primary method
- ✅ `getVenueBySlug(slug: String)` - Supplementary method
- ✅ `updateVenue(id: UUID, ...)` - Uses UUID
- ✅ `deleteVenue(id: UUID)` - Uses UUID
- ✅ `activateVenue(id: UUID)` - Uses UUID
- ✅ `suspendVenue(id: UUID)` - Uses UUID

### Controller Layer

- ✅ `GET /venues/{id}` - Primary endpoint
- ✅ `GET /venues/slug/{slug}` - SEO endpoint
- ✅ `PUT /admin/venues/{id}` - Uses UUID
- ✅ `DELETE /admin/venues/{id}` - Uses UUID

### DTO Layer

- ✅ VenueResponse includes both `id` and `slug`
- ✅ VenueDetailResponse includes both `id` and `slug`

---

## Thank You for the Correction! 🙏

You caught a significant design mistake. Using slugs for CRUD operations would have caused:

1. **Stability issues** - Slugs might change
2. **API contract breaks** - Existing clients would fail
3. **Poor maintainability** - Harder to track changes

The corrected approach follows **REST best practices**:

- **Stable UUIDs for operations**
- **Semantic codes for reference data**
- **Slugs as optional SEO enhancement**

This is now production-ready and follows industry standards! ✅

