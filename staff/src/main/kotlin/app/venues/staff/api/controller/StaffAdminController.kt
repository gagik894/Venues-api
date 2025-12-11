package app.venues.staff.api.controller

import app.venues.common.model.ApiResponse
import app.venues.shared.persistence.util.PageableMapper
import app.venues.staff.api.dto.*
import app.venues.staff.service.StaffManagementService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
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
        @RequestAttribute("staffId") actorId: UUID,
        @Valid @RequestBody req: InviteStaffRequest
    ): ApiResponse<StaffProfileDto> {
        logger.info { "Inviting ${req.email} to Org ${req.organizationId} as ${req.role}" }

        val profile = managementService.inviteStaff(actorId, req)

        return ApiResponse.success(
            data = profile,
            message = "Invitation processed successfully"
        )
    }

    @PostMapping("/create")
    @Operation(
        summary = "Create staff (direct)",
        description = "Admin creates a staff account with password and assigns org/venue roles"
    )
    fun createStaffDirect(
        @RequestAttribute("staffId") actorId: UUID,
        @Valid @RequestBody req: CreateStaffRequest
    ): ApiResponse<StaffProfileDto> {
        val profile = managementService.createStaffDirect(actorId, req)
        return ApiResponse.success(
            data = profile,
            message = "Staff account created successfully"
        )
    }

    @PostMapping("/resend-invite")
    @Operation(
        summary = "Resend staff invite",
        description = "Rotates invite token and resends email for a pending staff account"
    )
    fun resendInvite(
        @RequestAttribute("staffId") actorId: UUID,
        @Valid @RequestBody req: ResendInviteRequest
    ): ApiResponse<StaffProfileDto> {
        val profile = managementService.resendInvite(actorId, req)
        return ApiResponse.success(
            data = profile,
            message = "Invite resent successfully"
        )
    }

    @PostMapping("/revoke-invite")
    @Operation(
        summary = "Revoke staff invite",
        description = "Invalidates outstanding invite token for a pending staff account"
    )
    fun revokeInvite(
        @RequestAttribute("staffId") actorId: UUID,
        @Valid @RequestBody req: RevokeInviteRequest
    ): ApiResponse<StaffProfileDto> {
        val profile = managementService.revokeInvite(actorId, req)
        return ApiResponse.success(
            data = profile,
            message = "Invite revoked successfully"
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

    // ==============================
    // Listings for admin UI
    // ==============================

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List staff (super admin)")
    fun listStaff(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<StaffListItemDto>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val items = managementService.listAllStaff(pageable)
        return ApiResponse.success(items, "Staff listed")
    }

    @GetMapping("/organizations/{organizationId}/members")
    @Operation(summary = "List org members", description = "Requires SUPER_ADMIN or org OWNER/ADMIN")
    fun listOrgMembers(
        @RequestAttribute("staffId") actorId: UUID,
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<StaffListItemDto>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val items = managementService.listOrgMembers(actorId, organizationId, pageable)
        return ApiResponse.success(items, "Org members listed")
    }

    @GetMapping("/venues/{venueId}/permissions")
    @Operation(
        summary = "List venue permissions",
        description = "Requires SUPER_ADMIN or org OWNER/ADMIN of the venue's org"
    )
    fun listVenuePermissions(
        @RequestAttribute("staffId") actorId: UUID,
        @PathVariable venueId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?
    ): ApiResponse<Page<VenuePermissionDto>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val items = managementService.listVenuePermissions(actorId, venueId, pageable)
        return ApiResponse.success(items, "Venue permissions listed")
    }

    @PutMapping("/{id}/super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Set super admin flag",
        description = "Enable/disable super admin for a staff member (SUPER_ADMIN only)"
    )
    fun setSuperAdmin(
        @RequestAttribute("staffId") actorId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody req: SetSuperAdminRequest
    ): ApiResponse<Unit> {
        require(req.staffId == id) { "ID mismatch between path and body" }
        managementService.setSuperAdmin(actorId, req)
        return ApiResponse.success(message = "Super admin flag updated")
    }
}