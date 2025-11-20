# Common Module - Framework-Agnostic Foundation

## Overview

The `common` module provides a pure Kotlin/JPA foundation layer with **zero Spring Framework dependencies**. It contains
reusable utilities, base classes, and data structures that can be used in any JPA-compliant project.

## Purpose

- Provide stable, framework-agnostic building blocks
- Define core exception hierarchy
- Offer base entity classes with audit timestamps
- Supply validation utilities and constants
- Maintain pure DTOs for cross-cutting concerns

## Architecture Principle

```
┌─────────────────────────────────────────┐
│  COMMON (This Module)                   │
│  - Pure Kotlin + JPA APIs only          │
│  - No Spring dependencies               │
│  - Reusable across any framework        │
└────────────┬────────────────────────────┘
             │
             ├─► Can be used in Spring projects
             ├─► Can be used in Micronaut projects
             ├─► Can be used in plain Kotlin projects
             └─► Can be published as standalone library
```

## What Belongs Here

✅ **DO include**:

- Exception hierarchy (sealed classes)
- Framework-agnostic DTOs (pagination, responses)
- Application-wide constants
- ID generation utilities

❌ **DO NOT include**:

- Entity base classes (moved to `shared` module for Spring Data auditing)
- Spring annotations (`@Service`, `@Component`, etc.)
- Spring Data types (`Pageable`, `Page`, etc.)
- HTTP/REST framework code
- Database configuration
- Security/authentication logic
- Validation utilities (use Jakarta Bean Validation instead)
- String utilities (use Kotlin stdlib instead)

**Rule of Thumb**: If it requires a Spring application context to work, it belongs in `shared`, not `common`.

**Note**: Entity base classes (`AbstractUuidEntity`, `AbstractLongEntity`) have been moved to the `shared` module (
`app.venues.shared.persistence.domain`) to leverage Spring Data JPA auditing (`@CreatedDate`, `@LastModifiedDate`). This
provides more reliable timestamp management with less code.

---

## Module Contents

### 1. Exception Hierarchy

```kotlin
sealed class VenuesException(
    override val message: String,
    val errorCode: String,
    val httpStatus: Int,
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

**Subtypes**:

- `BusinessRuleViolation` (400) - Business logic errors
- `ResourceNotFound` (404) - Entity not found
- `AuthenticationFailure` (401) - Auth errors
- `AuthorizationFailure` (403) - Permission denied
- `ResourceConflict` (409) - Concurrency conflicts
- `ValidationFailure` (422) - Input validation errors
- `ExternalServiceFailure` (502) - Third-party API errors
- `RateLimitExceeded` (429) - Rate limiting

**Usage**:

```kotlin
// Throw with default message
throw VenuesException.ResourceNotFound()

// Throw with custom message
throw VenuesException.BusinessRuleViolation(
    message = "Seat is already reserved",
    errorCode = "SEAT_RESERVED"
)
```

---

### 3. Pagination Models

#### PaginationRequest

```kotlin
data class PaginationRequest(
    val limit: Int = 20,
    val offset: Int = 0,
    val sortBy: String? = null,
    val sortDirection: String? = null
) {
    fun validatedLimit(): Int = limit.coerceIn(1, 100)
    fun validatedOffset(): Int = offset.coerceAtLeast(0)
    fun validatedDirection(): SortDirection = // ...
    fun validatedSortBy(allowedFields: Set<String>): String? = // ...
}
```

**Features**:

- Framework-agnostic pagination parameters
- Built-in validation methods
- Type-safe sort direction
- Whitelist-based field validation

**Usage**:

```kotlin
val request = PaginationRequest(limit = 50, offset = 0, sortBy = "name")
val validLimit = request.validatedLimit()  // Coerced to [1, 100]
val sortField = request.validatedSortBy(setOf("name", "createdAt"))
```

#### PageMetadata

```kotlin
data class PageMetadata(
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)
```

**Usage**:

```kotlin
val metadata = PageMetadata.from(
    currentPage = 0,
    pageSize = 20,
    totalItems = 150L
)
// metadata.totalPages = 8, hasNext = true, hasPrevious = false
```

---

### 4. API Response Wrapper

```kotlin
@Serializable
data class ApiResponse<T>(
    val success: Boolean = true,
    val message: String = "Operation completed successfully",
    val data: T? = null,
    val timestamp: String,
    val metadata: ResponseMetadata? = null
)
```

**Factory Methods**:

```kotlin
// With data
ApiResponse.success(
    data = user,
    message = "User created successfully"
)

// Without data
ApiResponse.successNoData(
    message = "Password reset email sent"
)
```

---

### 4. ID Generator

```kotlin
object IdGenerator {
    /**
     * Generates a time-ordered UUID (v7).
     * Sequential for optimal database performance.
     */
    fun uuidv7(): UUID
}
```

**Why UUIDv7?**

- Chronologically sortable (includes timestamp)
- Prevents index fragmentation (better than UUIDv4)
- Globally unique (better than auto-increment)
- Database-agnostic

**Note on Validation**: Use Jakarta Bean Validation annotations (`@Email`, `@NotBlank`, `@Size`, etc.) instead of custom
validation utilities. This is the industry-standard approach and integrates seamlessly with Spring.

---

## Dependencies

### Runtime

```kotlin
api("org.jetbrains.kotlin:kotlin-stdlib")
api("org.jetbrains.kotlin:kotlin-reflect")
api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
api("org.slf4j:slf4j-api:2.0.16")
api("jakarta.validation:jakarta.validation-api:3.1.0")
api("jakarta.persistence:jakarta.persistence-api:3.1.0")
implementation("com.github.f4b6a3:uuid-creator:5.3.3")
```

### Key Points

- ✅ Only Jakarta (JPA/Validation) APIs - not Spring-specific
- ✅ SLF4J API only - no logging implementation
- ✅ No Spring Boot or Spring Data
- ✅ Serialization support via kotlinx.serialization

---

## Usage in Other Modules

### In Your build.gradle.kts

```kotlin
dependencies {
    api(project(":common"))  // For DTOs, exceptions, constants
    api(project(":shared"))  // For entity base classes and Spring integration
}
```

### Entity Example

```kotlin
import app.venues.shared.persistence.domain.AbstractUuidEntity

@Entity
@Table(name = "venues")
open class Venue(
    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, unique = true)
    var email: String
) : AbstractUuidEntity()  // Inherits id, createdAt, lastModifiedAt with Spring Data auditing

// Timestamps are automatically managed by Spring Data JPA auditing
// No need for @PrePersist or @PreUpdate callbacks
```

### Exception Handling Example

```kotlin
fun reserveSeat(seatId: UUID) {
    val seat = seatRepository.findById(seatId)
        ?: throw VenuesException.ResourceNotFound(
            message = "Seat not found",
            errorCode = "SEAT_NOT_FOUND"
        )

    if (seat.status != SeatStatus.AVAILABLE) {
        throw VenuesException.ResourceConflict(
            message = "Seat is already reserved",
            errorCode = "SEAT_ALREADY_RESERVED"
        )
    }
}
```

---

## Integration with Spring (via Shared Module)

The `shared` module provides Spring-specific adapters:

```kotlin
// Common module (framework-agnostic)
val request = PaginationRequest(limit = 20, offset = 0)

// Shared module converts to Spring Data Pageable
val pageable = PageableMapper.toPageable(
    request,
    allowedSortFields = setOf("name", "createdAt")
)

// Use in repository
val page = userRepository.findAll(pageable)

// Convert back to common DTO
val metadata = PageableMapper.toPageMetadata(page)
```

---

## Testing

### Unit Tests

```kotlin
class PaginationRequestTest {
    @Test
    fun `validatedLimit should coerce to valid range`() {
        val request = PaginationRequest(limit = 999)
        assertEquals(100, request.validatedLimit())  // Max is 100
    }

    @Test
    fun `validatedOffset should be non-negative`() {
        val request = PaginationRequest(offset = -5)
        assertEquals(0, request.validatedOffset())
    }
}
```

### Integration Tests

```kotlin
@Test
fun `entity timestamps should be set on persist`() {
    val venue = Venue(name = "Test", email = "test@example.com")
    entityManager.persist(venue)
    entityManager.flush()

    assertNotNull(venue.createdAt)
    assertNotNull(venue.lastModifiedAt)
}
```

---

## Best Practices

### 1. Exception Handling

✅ **DO**: Use specific exception types

```kotlin
throw VenuesException.ResourceNotFound("User not found")
```

❌ **DON'T**: Use generic RuntimeException

```kotlin
throw RuntimeException("User not found")
```

### 2. Validation

✅ **DO**: Use Jakarta Bean Validation annotations

```kotlin
data class CreateUserRequest(
    @field:Email(message = "Invalid email format")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    val password: String
)
```

❌ **DON'T**: Let invalid data propagate

```kotlin
userRepository.save(User(email = request.email))  // No validation
```

### 3. Entity Design

✅ **DO**: Use appropriate ID strategy

```kotlin
// High-volume, single DB
class CartItem : AbstractLongEntity()

// Distributed, global uniqueness
class Event : AbstractUuidEntity()
```

### 4. Pagination

✅ **DO**: Use PaginationRequest in services

```kotlin
fun getUsers(request: PaginationRequest): List<User> {
    val limit = request.validatedLimit()
    // Use limit in query
}
```

---

## Migration Guide

### From Spring-Dependent Common to Pure Common

If you have existing code using old patterns:

#### Entity Timestamps

```kotlin
// OLD (Spring Data)
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
class MyEntity {
    @CreatedDate
    var createdAt: Instant? = null
}

// NEW (Pure JPA)
class MyEntity : AbstractUuidEntity() {
    // createdAt inherited, managed by @PrePersist
}
```

#### Pagination

```kotlin
// OLD (Spring Data in common)
import org.springframework.data.domain.Pageable
fun getUsers(pageable: Pageable): Page<User>

// NEW (Framework-agnostic)
import app . venues . common . model . PaginationRequest
fun getUsers(request: PaginationRequest): List<User>
```

---

## FAQ

### Q: Why not just use Spring Data annotations?

**A**: To keep `common` reusable in non-Spring projects and avoid unnecessary dependencies.

### Q: Can I use this module in a Micronaut project?

**A**: Yes! It has zero Spring dependencies. You'll need to create Micronaut-specific adapters (similar to our `shared`
module).

### Q: Why UUIDv7 instead of UUIDv4?

**A**: UUIDv7 is chronologically ordered, preventing database index fragmentation and improving query performance.

### Q: Can I add Spring annotations to common module?

**A**: No. If you need Spring-specific code, put it in `shared` module.

---

## Contributing

### Adding New Utilities

1. Ensure zero Spring dependencies
2. Add comprehensive KDoc
3. Include usage examples
4. Add unit tests
5. Update this README

### Code Review Checklist

- [ ] No Spring imports
- [ ] Works with pure JPA
- [ ] Documented with KDoc
- [ ] Unit tests included
- [ ] README updated

---

## Version History

### v1.1.0 (November 19, 2025)

- ✅ Removed all Spring dependencies
- ✅ Replaced Spring Data auditing with JPA callbacks
- ✅ Created framework-agnostic pagination models
- ✅ Added comprehensive documentation

### v1.0.0 (Initial)

- Base entity classes
- Exception hierarchy
- Validation utilities

---

## Related Documentation

- [Shared Module README](../shared/README.md) - Spring integration layer
- [Architecture Overview](../docs/ARCHITECTURE.md)
- [Common Module Cleanup](../docs/COMMON_MODULE_SPRING_CLEANUP.md)

---

*This module follows government-quality coding standards with SOLID principles, comprehensive documentation, and zero
technical debt.*

