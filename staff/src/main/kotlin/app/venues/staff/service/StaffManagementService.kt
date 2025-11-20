package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.staff.api.dto.InviteStaffRequest
import app.venues.staff.api.dto.StaffGlobalContextDto
import app.venues.staff.api.dto.StaffProfileDto
import app.venues.staff.api.dto.UpdateStaffStatusRequest
import app.venues.staff.domain.StaffIdentity
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
 * - Staff invitations
 * - Membership management
 * - Status updates
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
     */
    fun getStaffContext(staffId: UUID): StaffGlobalContextDto {
        logger.debug { "Fetching context for staff: $staffId" }
        return staffContextBuilder.buildContextById(staffId)
    }

    /**
     * Invites a user to an organization.
     *
     * Process:
     * 1. Check if email already exists (find or create staff identity)
     * 2. Create or update membership
     * 3. Return profile
     *
     * Note: For new users, this only creates the membership.
     *       They still need to register/verify to actually log in.
     */
    fun inviteStaff(request: InviteStaffRequest): StaffProfileDto {
        logger.info { "Inviting ${request.email} to org ${request.organizationId} as ${request.role}" }

        // TODO: Security check - verify current user is ADMIN of request.organizationId

        // Find or create staff identity
        var staff = staffRepository.findByEmail(request.email.lowercase().trim())

        if (staff == null) {
            // Create placeholder identity (they'll complete registration later)
            staff = StaffIdentity(
                email = request.email.lowercase().trim(),
                passwordHash = "", // Empty - will be set during registration
                status = StaffStatus.PENDING_VERIFICATION,
                isPlatformSuperAdmin = false
            )
            staff = staffRepository.save(staff)
            logger.info { "Created new staff identity for invitation: ${staff.email}" }
        }

        // Check if membership already exists
        val existingMembership = staff.memberships.firstOrNull {
            it.organizationId == request.organizationId
        }

        if (existingMembership != null) {
            // Update existing membership
            existingMembership.orgRole = request.role
            existingMembership.isActive = true
            logger.info { "Updated existing membership for ${staff.email}" }
        } else {
            // Create new membership
            val membership = StaffMembership(
                staff = staff,
                organizationId = request.organizationId,
                orgRole = request.role,
                isActive = true
            )
            staff.memberships.add(membership)
            logger.info { "Created new membership for ${staff.email}" }
        }

        staffRepository.save(staff)

        return StaffProfileDto(
            id = staff.id,
            email = staff.email,
            firstName = staff.firstName,
            lastName = staff.lastName,
            status = staff.status,
            isPlatformSuperAdmin = staff.isPlatformSuperAdmin
        )
    }

    /**
     * Updates staff status (suspend/reactivate/etc).
     *
     * Note: This is a system admin operation.
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
