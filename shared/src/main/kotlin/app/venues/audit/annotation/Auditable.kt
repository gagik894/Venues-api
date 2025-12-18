package app.venues.audit.annotation

/**
 * Marks a controller endpoint for automatic audit logging.
 *
 * Usage:
 * @PostMapping("/venues")
 * @Auditable(action = "VENUE_CREATE", subjectType = "venue")
 * fun createVenue(...): ApiResponse<VenueResponse> { ... }
 *
 * The aspect will:
 * 1. Extract staffId from @RequestAttribute or SecurityContext
 * 2. Extract venueId from @PathVariable if present
 * 3. Call auditActionRecorder with method result
 * 4. Support success/failure based on exception
 *
 * Metadata can be extracted from method parameters annotated with @AuditMetadata.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Auditable(
    /**
     * The audit action code (e.g., VENUE_CREATE, STAFF_UPDATE).
     * Will be uppercased and used as-is.
     */
    val action: String,

    /**
     * Type of subject being acted upon (venue, event, booking, etc).
     */
    val subjectType: String,

    /**
     * Optional: If true, extract venueId from @PathVariable.
     * If false, venueId will be null in audit record.
     */
    val includeVenueId: Boolean = true,

    /**
     * Optional: If true, extract organizationId from @PathVariable.
     */
    val includeOrganizationId: Boolean = false
)

/**
 * Marks a parameter to be included in audit metadata.
 *
 * Usage:
 * @Auditable(action = "USER_UPDATE", subjectType = "user")
 * fun updateUser(
 *     @PathVariable userId: UUID,
 *     @AuditMetadata("email") @RequestBody request: UpdateUserRequest
 * ): ApiResponse<UserResponse> { ... }
 *
 * The aspect will extract the entire request object and add it to metadata.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuditMetadata(
    /**
     * Key in the metadata map for this parameter.
     */
    val value: String
)
