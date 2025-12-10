package app.venues.staff.api.controller

import app.venues.common.model.ApiResponse
import app.venues.staff.api.dto.*
import app.venues.staff.service.StaffManagementService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@PreAuthorize("hasRole('STAFF') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff Management", description = "Manage authorized venues, invitations, and statuses")
class StaffAdminController(
    private val managementService: StaffManagementService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Critical endpoint for the Frontend Sidebar.
     * Returns the list of venues the user can access with their assigned roles.
     */
    @GetMapping("/me/authorized-venues")
    @Operation(
        summary = "Get Authorized Venues",
        description = "Returns the list of venues this user can access with their roles"
    )
    fun getMyAuthorizedVenues(
        @RequestAttribute("staffId") staffId: UUID
    ): ApiResponse<List<AuthorizedVenueDto>> {
        // Note: "staffId" usually comes from a JWT Filter/Interceptor
        logger.debug { "Fetching authorized venues for staff: $staffId" }

        val venues = managementService.getAuthorizedVenues(staffId)

        return ApiResponse.success(
            data = venues,
            message = "Authorized venues retrieved successfully"
        )
    }

    @PostMapping("/invite")
    @Operation(summary = "Invite Member", description = "Invite a user (new or existing) to an Organization")
    fun inviteStaff(
        @Valid @RequestBody req: InviteStaffRequest
    ): ApiResponse<StaffProfileDto> {
        logger.info { "Inviting ${req.email} to Org ${req.organizationId} as ${req.role}" }

        // Security Note: Service layer must verify that Current User is ADMIN of req.organizationId
        val profile = managementService.inviteStaff(req)

        return ApiResponse.success(
            data = profile,
            message = "Invitation processed successfully"
        )
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update Status", description = "Suspend or Reactivate a staff member (System Admin only)")
    fun updateStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateStaffStatusRequest
    ): ApiResponse<Unit> {
        if (id != req.staffId) throw IllegalArgumentException("ID mismatch in path and body")

        logger.warn { "Updating status of staff $id to ${req.status}" }

        managementService.updateStatus(req)

        return ApiResponse.success(
            message = "Staff status updated"
        )
    }

    @PostMapping("/venue-permissions")
    @Operation(
        summary = "Grant venue permission",
        description = "Grant a venue-level role (MANAGER/EDITOR/SCANNER/VIEWER) to a staff member"
    )
    fun grantVenuePermission(
        @RequestAttribute("staffId") actorId: UUID,
        @Valid @RequestBody req: GrantVenuePermissionRequest
    ): ApiResponse<StaffProfileDto> {
        val profile = managementService.grantVenuePermission(actorId, req)
        return ApiResponse.success(profile, "Venue permission granted")
    }
}