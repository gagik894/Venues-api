package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.common.model.ResponseMetadata
import app.venues.shared.persistence.util.PageableMapper
import app.venues.staff.api.dto.*
import app.venues.staff.api.mapper.StaffMapper
import app.venues.staff.domain.StaffMembership
import app.venues.staff.domain.StaffStatus
import app.venues.staff.domain.StaffVenuePermission
import app.venues.staff.repository.StaffIdentityRepository
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for staff management operations.
 *
 * Responsibilities:
 * - Staff authorized venues retrieval
 * - Staff invitations to organizations
 * - Membership management
 * - Status updates (suspend/reactivate)
 */
@Service
class StaffManagementService(
    private val staffRepository: StaffIdentityRepository,
    private val staffContextBuilder: StaffContextBuilder,
    private val venueApi: VenueApi
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Gets the authorized venues list for a staff member.
     *
     * Returns which venues they can access with their assigned roles.
     * Used by the frontend to build navigation/sidebar.
     *
     * @param staffId Staff member ID
     * @return List of authorized venues with roles
     */
    @Transactional(readOnly = true)
    fun getAuthorizedVenues(staffId: UUID): List<AuthorizedVenueDto> {
        logger.debug { "Fetching authorized venues for staff: $staffId" }
        return staffContextBuilder.buildAuthorizedVenuesById(staffId)
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
    @Transactional
    fun inviteStaff(actorId: UUID, request: InviteStaffRequest): StaffProfileDto {
        logger.info { "Inviting ${request.email} to org ${request.organizationId} as ${request.role}" }

        // Authorization
        enforceOrgAdmin(actorId, request.organizationId)

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
    @Transactional
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

    /**
     * Grants a venue-level permission to a staff member.
     *
     * Rules:
     * - SUPER_ADMIN can grant any permission.
     * - Organization OWNER/ADMIN of the venue's organization can grant.
     * - Target staff must belong to the same organization; if not, a MEMBER membership is created.
     */
    @Transactional
    fun grantVenuePermission(actorId: UUID, request: GrantVenuePermissionRequest): StaffProfileDto {
        val venueOrgId = venueApi.getVenueOrganizationId(request.venueId)
            ?: throw VenuesException.ResourceNotFound(
                "Venue not found: ${request.venueId}",
                "VENUE_NOT_FOUND"
            )

        val actor = staffRepository.findById(actorId).orElseThrow {
            VenuesException.ResourceNotFound("Actor not found", "STAFF_NOT_FOUND")
        }

        // Authorization: super admin OR org OWNER/ADMIN for the venue's org
        if (!actor.isPlatformSuperAdmin) {
            val actorMembership = actor.memberships.firstOrNull { it.organizationId == venueOrgId && it.isActive }
                ?: throw VenuesException.AuthorizationFailure("Not authorized to manage this venue")
            if (actorMembership.orgRole !in listOf(
                    app.venues.staff.domain.OrganizationRole.OWNER,
                    app.venues.staff.domain.OrganizationRole.ADMIN
                )
            ) {
                throw VenuesException.AuthorizationFailure("Not authorized to manage this venue")
            }
        }

        val target = staffRepository.findById(request.staffId).orElseThrow {
            VenuesException.ResourceNotFound("Staff not found", "STAFF_NOT_FOUND")
        }

        // Ensure target has membership in org; create MEMBER if missing
        val membership = target.memberships.firstOrNull { it.organizationId == venueOrgId }
            ?: StaffMembership(
                staff = target,
                organizationId = venueOrgId,
                orgRole = app.venues.staff.domain.OrganizationRole.MEMBER,
                isActive = true
            ).also { target.memberships.add(it) }

        membership.isActive = true

        val existingPerm = membership.venuePermissions.firstOrNull { it.venueId == request.venueId }
        if (existingPerm != null) {
            existingPerm.role = request.role
        } else {
            membership.venuePermissions.add(
                StaffVenuePermission(
                    membership = membership,
                    venueId = request.venueId,
                    role = request.role
                )
            )
        }

        staffRepository.save(target)
        logger.info { "Granted ${request.role} for venue ${request.venueId} to ${target.email}" }

        return StaffMapper.toProfileDto(target)
    }

    // ==============================
    // Listings
    // ==============================

    @Transactional(readOnly = true)
    fun listAllStaff(limit: Int?, offset: Int?): Pair<List<StaffListItemDto>, ResponseMetadata> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val page = staffRepository.findAll(pageable)
        val venueMap = resolveVenues(page.content)
        val data = page.content.map { toListItem(it, venueMap) }
        val metadata = toMetadata(page)
        return data to metadata
    }

    @Transactional(readOnly = true)
    fun listOrgMembers(
        actorId: UUID,
        organizationId: UUID,
        limit: Int?,
        offset: Int?
    ): Pair<List<StaffListItemDto>, ResponseMetadata> {
        enforceOrgAdmin(actorId, organizationId)
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val members = staffRepository.findAllByOrganizationId(organizationId, pageable)
        val venueMap = resolveVenues(members.content)
        val data = members.content.map { toListItem(it, venueMap) }
        val metadata = toMetadata(members)
        return data to metadata
    }

    @Transactional(readOnly = true)
    fun listVenuePermissions(
        actorId: UUID,
        venueId: UUID,
        limit: Int?,
        offset: Int?
    ): Pair<List<VenuePermissionDto>, ResponseMetadata> {
        val orgId = venueApi.getVenueOrganizationId(venueId)
            ?: throw VenuesException.ResourceNotFound("Venue not found", "VENUE_NOT_FOUND")
        enforceOrgAdmin(actorId, orgId)

        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val staffWithPerms = staffRepository.findAllByVenueId(venueId, pageable)
        val data = staffWithPerms.content.flatMap { staff ->
            staff.memberships.flatMap { m ->
                m.venuePermissions
                    .filter { it.venueId == venueId }
                    .map {
                        VenuePermissionDto(
                            staffId = staff.id!!,
                            staffEmail = staff.email,
                            role = it.role
                        )
                    }
            }
        }
        val metadata = toMetadata(staffWithPerms)
        return data to metadata
    }

    private fun toListItem(
        staff: app.venues.staff.domain.StaffIdentity,
        venueMap: Map<UUID, app.venues.venue.api.dto.VenueBasicInfoDto>
    ): StaffListItemDto {
        val orgs = staff.memberships.filter { it.isActive }
            .map { OrganizationAccessDto(id = it.organizationId, role = it.orgRole) }
        val venues = staff.memberships.flatMap { m ->
            m.venuePermissions.map { vp ->
                AuthorizedVenueDto(
                    id = vp.venueId,
                    name = venueMap[vp.venueId]?.name ?: "",
                    slug = venueMap[vp.venueId]?.slug ?: "",
                    role = vp.role
                )
            }
        }
        return StaffListItemDto(
            id = staff.id!!,
            email = staff.email,
            firstName = staff.firstName,
            lastName = staff.lastName,
            status = staff.status,
            isSuperAdmin = staff.isPlatformSuperAdmin,
            organizations = orgs,
            venueRoles = venues
        )
    }

    private fun enforceOrgAdmin(actorId: UUID, organizationId: UUID) {
        val actor = staffRepository.findById(actorId).orElseThrow {
            VenuesException.AuthorizationFailure("Not authorized")
        }
        if (actor.isPlatformSuperAdmin) return
        val membership = actor.memberships.firstOrNull { it.organizationId == organizationId && it.isActive }
            ?: throw VenuesException.AuthorizationFailure("Not authorized")
        if (membership.orgRole !in listOf(
                app.venues.staff.domain.OrganizationRole.OWNER,
                app.venues.staff.domain.OrganizationRole.ADMIN
            )
        ) {
            throw VenuesException.AuthorizationFailure("Not authorized")
        }
    }

    private fun resolveVenues(staffList: List<app.venues.staff.domain.StaffIdentity>): Map<UUID, app.venues.venue.api.dto.VenueBasicInfoDto> {
        val ids = staffList
            .flatMap { it.memberships }
            .flatMap { it.venuePermissions }
            .map { it.venueId }
            .toSet()
        if (ids.isEmpty()) return emptyMap()
        return venueApi.getVenueBasicInfoBatch(ids)
    }

    @Transactional
    fun setSuperAdmin(actorId: UUID, request: SetSuperAdminRequest) {
        // Only super admin can set super admin
        val actor = staffRepository.findById(actorId).orElseThrow {
            VenuesException.AuthorizationFailure("Not authorized")
        }
        if (!actor.isPlatformSuperAdmin) {
            throw VenuesException.AuthorizationFailure("Not authorized")
        }
        val target = staffRepository.findById(request.staffId).orElseThrow {
            VenuesException.ResourceNotFound("Staff not found", "STAFF_NOT_FOUND")
        }
        target.isPlatformSuperAdmin = request.isSuperAdmin
        staffRepository.save(target)
        logger.info { "Super admin flag updated for ${target.email}: ${request.isSuperAdmin}" }
    }

    private fun toMetadata(page: Page<*>): ResponseMetadata {
        return ResponseMetadata(
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )
    }
}
