package app.venues.staff.api.controller

import app.venues.common.model.ApiResponse
import app.venues.staff.api.dto.InviteStaffRequest
import app.venues.staff.api.dto.StaffGlobalContextDto
import app.venues.staff.api.dto.StaffProfileDto
import app.venues.staff.api.dto.UpdateStaffStatusRequest
import app.venues.staff.service.StaffManagementService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff Management", description = "Manage context, invitations, and statuses")
class StaffAdminController(
    private val managementService: StaffManagementService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Critical endpoint for the Frontend Sidebar.
     * It tells the UI which Organizations and Venues the user can access.
     */
    @GetMapping("/me/context")
    @Operation(
        summary = "Get User Context",
        description = "Returns the hierarchy of Organizations and Venues this user can access"
    )
    fun getMyContext(@RequestAttribute("principalId") staffId: UUID): ApiResponse<StaffGlobalContextDto> {
        // Note: "staffId" usually comes from a JWT Filter/Interceptor
        logger.debug { "Fetching context for staff: $staffId" }

        val context = managementService.getStaffContext(staffId)

        return ApiResponse.success(
            data = context,
            message = "Context retrieved successfully"
        )
    }

    @PostMapping("/invite")
    @Operation(summary = "Invite Member", description = "Invite a user (new or existing) to an Organization")
    fun inviteStaff(@Valid @RequestBody req: InviteStaffRequest): ApiResponse<StaffProfileDto> {
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
}