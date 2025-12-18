package app.venues.audit.example

import app.venues.audit.annotation.AuditMetadata
import app.venues.audit.annotation.Auditable
import app.venues.common.model.ApiResponse
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Example controller showing how to use @Auditable for automatic audit logging.
 *
 * This is a reference implementation. Copy patterns to your own controllers.
 */
@RestController
@RequestMapping("/api/v1/example/venues")
class ExampleAuditableController {

    /**
     * Example 1: Basic audit with action and subject type.
     * The aspect will automatically:
     * - Extract staffId from @RequestAttribute
     * - Extract venueId from @PathVariable
     * - Extract id from response data
     */
    @PostMapping
    @Auditable(action = "VENUE_CREATE", subjectType = "venue")
    fun createVenue(
        @RequestAttribute staffId: UUID,
        @RequestBody request: CreateVenueRequest
    ): ApiResponse<VenueResponse> {
        // Business logic here - NO manual audit calls needed!
        val venue = VenueResponse(id = UUID.randomUUID(), name = request.name)
        return ApiResponse.success(venue, "Venue created")
    }

    /**
     * Example 2: Audit with path variable extraction.
     * venueId will be automatically captured from @PathVariable.
     */
    @PutMapping("/{venueId}")
    @Auditable(action = "VENUE_UPDATE", subjectType = "venue", includeVenueId = true)
    fun updateVenue(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @RequestBody request: UpdateVenueRequest
    ): ApiResponse<VenueResponse> {
        val venue = VenueResponse(id = venueId, name = request.name)
        return ApiResponse.success(venue, "Venue updated")
    }

    /**
     * Example 3: Audit without venueId (organization-level operation).
     * includeVenueId=false means venueId will be null in audit record.
     */
    @PostMapping("/{orgId}/settings")
    @Auditable(
        action = "ORG_SETTINGS_UPDATE",
        subjectType = "organization",
        includeVenueId = false,
        includeOrganizationId = true
    )
    fun updateOrgSettings(
        @RequestAttribute staffId: UUID,
        @PathVariable("orgId") organizationId: UUID,
        @RequestBody request: OrgSettingsRequest
    ): ApiResponse<Unit> {
        return ApiResponse.success(Unit, "Settings updated")
    }

    /**
     * Example 4: Include additional metadata.
     * @AuditMetadata marks parameters to include in audit metadata map.
     */
    @PatchMapping("/{venueId}/logo")
    @Auditable(action = "VENUE_LOGO_UPDATE", subjectType = "venue")
    fun updateLogo(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID,
        @AuditMetadata("logoUrl") @RequestBody logoRequest: LogoUpdateRequest
    ): ApiResponse<VenueResponse> {
        val venue = VenueResponse(id = venueId, name = "Updated Venue")
        return ApiResponse.success(venue, "Logo updated")
    }

    /**
     * Example 5: Delete operation (automatic failure handling).
     * If an exception is thrown, @Auditable aspect will catch it and record FAILURE.
     */
    @DeleteMapping("/{venueId}")
    @Auditable(action = "VENUE_DELETE", subjectType = "venue")
    fun deleteVenue(
        @RequestAttribute staffId: UUID,
        @PathVariable venueId: UUID
    ): ApiResponse<Unit> {
        // If this throws an exception, aspect will record it as FAILURE
        // with errorType and errorMessage in metadata
        return ApiResponse.success(Unit, "Venue deleted")
    }
}

// DTOs
data class CreateVenueRequest(val name: String)
data class UpdateVenueRequest(val name: String)
data class OrgSettingsRequest(val setting: String)
data class LogoUpdateRequest(val logoUrl: String)
data class VenueResponse(val id: UUID, val name: String)
