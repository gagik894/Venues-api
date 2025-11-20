package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.staff.api.dto.InviteStaffRequest
import app.venues.staff.api.dto.StaffGlobalContextDto
import app.venues.staff.api.dto.StaffProfileDto
import app.venues.staff.api.dto.UpdateStaffStatusRequest
import app.venues.staff.api.mapper.StaffMapper
import app.venues.staff.domain.StaffMembership
import app.venues.staff.domain.StaffStatus
import app.venues.staff.repository.StaffIdentityRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for staff management operations.
 *
 * Responsibilities:
 * - Staff context/hierarchy retrieval
 * - Staff invitations to organizations
 * - Membership management
 * - Status updates (suspend/reactivate)
 */
@Service
@Transactional
class StaffManagementService(
    private val staffRepository: StaffIdentityRepository,
    private val staffContextBuilder: StaffContextBuilder
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Gets the organizational context for a staff member.
     *
     * Returns which organizations and venues they can access.
     * Used by the frontend to build navigation/sidebar.
     *
     * @param staffId Staff member ID
     * @return StaffGlobalContextDto with organizations and venues hierarchy
     */
    @Transactional(readOnly = true)
    fun getStaffContext(staffId: UUID): StaffGlobalContextDto {
        logger.debug { "Fetching context for staff: $staffId" }
        return staffContextBuilder.buildContextById(staffId)
    }

    /**
     * Invites a staff member to an organization.
     *
     * Process:
     * 1. Validate staff identity exists
     * 2. Check if membership already exists
     * 3. Create or update membership with specified role
     * 4. Return profile
     *
     * Note: This only manages membership. The staff member must already have
     * a registered account (via register endpoint). If they don't have an account,
     * they should be directed to register first.
     *
     * Security: Caller must be verified as OWNER or ADMIN of the organization.
     * This validation should be done at the controller/security layer.
     *
     * @param request Invitation request with email, organizationId, and role
     * @return StaffProfileDto of the invited staff member
     * @throws VenuesException.ResourceNotFound if staff identity doesn't exist
     */
    fun inviteStaff(request: InviteStaffRequest): StaffProfileDto {
        logger.info { "Inviting ${request.email} to org ${request.organizationId} as ${request.role}" }

        // Find existing staff identity
        val staff = staffRepository.findByEmail(request.email.lowercase().trim())
            ?: throw VenuesException.ResourceNotFound(
                "No staff account found with email: ${request.email}. They must register first.",
                "STAFF_NOT_FOUND"
            )

        // Check if membership already exists
        val existingMembership = staff.memberships.firstOrNull {
            it.organizationId == request.organizationId
        }

        if (existingMembership != null) {
            // Update existing membership
            existingMembership.orgRole = request.role
            existingMembership.isActive = true
            logger.info { "Updated existing membership for ${staff.email} in org ${request.organizationId}" }
        } else {
            // Create new membership
            val membership = StaffMembership(
                staff = staff,
                organizationId = request.organizationId,
                orgRole = request.role,
                isActive = true
            )
            staff.memberships.add(membership)
            logger.info { "Created new membership for ${staff.email} in org ${request.organizationId}" }
        }

        staffRepository.save(staff)

        // TODO: Send invitation email notifying staff of new organization access

        return StaffMapper.toProfileDto(staff)
    }

    /**
     * Updates staff status (suspend/reactivate/etc).
     *
     * This is a privileged operation typically restricted to system administrators.
     *
     * Security: Caller must be verified as SUPER_ADMIN.
     * This validation should be done at the controller/security layer.
     *
     * @param request Status update request
     * @throws VenuesException.ResourceNotFound if staff not found
     */
    fun updateStatus(request: UpdateStaffStatusRequest) {
        logger.warn { "Updating status of staff ${request.staffId} to ${request.status}" }

        val staff = staffRepository.findById(request.staffId)
            .orElseThrow {
                VenuesException.ResourceNotFound(
                    "Staff not found",
                    "STAFF_NOT_FOUND"
                )
            }

        when (request.status) {
            StaffStatus.SUSPENDED -> staff.suspend()
            StaffStatus.ACTIVE -> staff.reactivate()
            else -> staff.status = request.status
        }

        staffRepository.save(staff)

        logger.info { "Staff ${request.staffId} status updated to ${request.status}" }
    }
}
