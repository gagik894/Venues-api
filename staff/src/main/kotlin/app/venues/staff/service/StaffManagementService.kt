package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.shared.email.EmailService
import app.venues.shared.email.EmailTemplateService
import app.venues.staff.api.dto.*
import app.venues.staff.api.mapper.StaffMapper
import app.venues.staff.domain.*
import app.venues.staff.repository.StaffIdentityRepository
import app.venues.staff.repository.StaffVenuePermissionRepository
import app.venues.venue.api.VenueApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
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
    private val venuePermissionRepository: StaffVenuePermissionRepository,
    private val venueApi: VenueApi,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    @Value("\${app.frontend.url}") private val frontendBaseUrl: String
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val INVITE_TOKEN_TTL_SECONDS: Long = 72 * 60 * 60 // 72 hours
    }

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

        enforceOrgAdmin(actorId, request.organizationId)
        validateVenueOwnership(request.organizationId, request.venuePermissions)

        val email = request.email.lowercase().trim()
        val existing = staffRepository.findByEmail(email)

        val staff = existing ?: StaffIdentity(
            email = email,
            passwordHash = passwordEncoder.encode(UUID.randomUUID().toString()),
            status = StaffStatus.PENDING_VERIFICATION,
            preferredLanguage = request.preferredLanguage ?: "en"
        ).apply {
            verificationToken = UUID.randomUUID().toString()
            verificationTokenExpiresAt = Instant.now().plusSeconds(INVITE_TOKEN_TTL_SECONDS)
        }

        val membership = upsertMembership(staff, request.organizationId, request.role)
        applyVenuePermissions(membership, request.venuePermissions)

        // Refresh invite token if user exists but still pending and we need to send email
        if (request.sendEmail && staff.status != StaffStatus.ACTIVE) {
            staff.verificationToken = UUID.randomUUID().toString()
            staff.verificationTokenExpiresAt = Instant.now().plusSeconds(INVITE_TOKEN_TTL_SECONDS)
        }

        val saved = staffRepository.save(staff)

        if (request.sendEmail) {
            sendInvitationEmail(saved)
        }

        return StaffMapper.toProfileDto(saved)
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

    /**
     * Directly creates an active staff account (admin-driven).
     *
     * - Super admin can set platform super admin flag.
     * - Org owner/admin can create within their org only and cannot set super admin.
     */
    @Transactional
    fun createStaffDirect(actorId: UUID, request: CreateStaffRequest): StaffProfileDto {
        val actor = staffRepository.findById(actorId).orElseThrow {
            VenuesException.AuthorizationFailure("Not authorized")
        }
        val actorIsSuperAdmin = actor.isPlatformSuperAdmin
        if (!actorIsSuperAdmin) {
            enforceOrgAdmin(actorId, request.organizationId)
        }
        if (request.isSuperAdmin && !actorIsSuperAdmin) {
            throw VenuesException.AuthorizationFailure("Only super admin can create a super admin")
        }

        validateVenueOwnership(request.organizationId, request.venuePermissions)

        val email = request.email.lowercase().trim()
        if (staffRepository.existsByEmail(email)) {
            throw VenuesException.ResourceConflict(
                "A staff account with this email address already exists",
                "EMAIL_ALREADY_EXISTS"
            )
        }

        val staff = StaffIdentity(
            email = email,
            passwordHash = passwordEncoder.encode(request.password),
            firstName = request.firstName?.trim(),
            lastName = request.lastName?.trim(),
            status = StaffStatus.ACTIVE,
            isPlatformSuperAdmin = request.isSuperAdmin,
            preferredLanguage = request.preferredLanguage ?: "en"
        )

        val membership = StaffMembership(
            staff = staff,
            organizationId = request.organizationId,
            orgRole = request.role,
            isActive = true
        )
        staff.memberships.add(membership)
        applyVenuePermissions(membership, request.venuePermissions)

        val saved = staffRepository.save(staff)

        if (request.sendEmail) {
            sendAccountReadyEmail(saved)
        }

        return StaffMapper.toProfileDto(saved)
    }

    /**
     * Resends an invite by rotating the token and emailing the user (pending accounts only).
     */
    @Transactional
    fun resendInvite(actorId: UUID, request: ResendInviteRequest): StaffProfileDto {
        enforceOrgAdmin(actorId, request.organizationId)

        val staff = staffRepository.findById(request.staffId).orElseThrow {
            VenuesException.ResourceNotFound("Staff not found", "STAFF_NOT_FOUND")
        }

        val membership = upsertMembership(
            staff = staff,
            organizationId = request.organizationId,
            orgRole = staff.memberships.firstOrNull { it.organizationId == request.organizationId }?.orgRole
                ?: OrganizationRole.MEMBER
        )

        if (staff.status == StaffStatus.ACTIVE) {
            throw VenuesException.BusinessRuleViolation(
                "Staff is already active",
                "INVITE_NOT_POSSIBLE_ACTIVE"
            )
        }

        staff.verificationToken = UUID.randomUUID().toString()
        staff.verificationTokenExpiresAt = Instant.now().plusSeconds(INVITE_TOKEN_TTL_SECONDS)
        val saved = staffRepository.save(staff)

        if (request.sendEmail) {
            sendInvitationEmail(saved)
        }

        logger.info { "Resent invite to ${staff.email} for org ${request.organizationId} (membership ${membership.id})" }
        return StaffMapper.toProfileDto(saved)
    }

    /**
     * Revokes an outstanding invite token (keeps membership but invalidates token).
     */
    @Transactional
    fun revokeInvite(actorId: UUID, request: RevokeInviteRequest): StaffProfileDto {
        enforceOrgAdmin(actorId, request.organizationId)

        val staff = staffRepository.findById(request.staffId).orElseThrow {
            VenuesException.ResourceNotFound("Staff not found", "STAFF_NOT_FOUND")
        }

        upsertMembership(
            staff = staff,
            organizationId = request.organizationId,
            orgRole = staff.memberships.firstOrNull { it.organizationId == request.organizationId }?.orgRole
                ?: OrganizationRole.MEMBER
        )

        if (staff.status == StaffStatus.ACTIVE) {
            throw VenuesException.BusinessRuleViolation(
                "Staff is already active",
                "REVOKE_NOT_POSSIBLE_ACTIVE"
            )
        }

        staff.verificationToken = null
        staff.verificationTokenExpiresAt = null
        val saved = staffRepository.save(staff)

        logger.info { "Revoked invite for ${staff.email} in org ${request.organizationId}" }
        return StaffMapper.toProfileDto(saved)
    }

    // ==============================
    // Listings
    // ==============================

    @Transactional(readOnly = true)
    fun listAllStaff(pageable: Pageable): Page<StaffListItemDto> {
        val page = staffRepository.findAll(pageable)
        val venueMap = resolveVenues(page.content)
        val data = page.content.map { toListItem(it, venueMap) }
        return PageImpl(data, pageable, page.totalElements)
    }

    @Transactional(readOnly = true)
    fun listOrgMembers(
        actorId: UUID,
        organizationId: UUID,
        pageable: Pageable
    ): Page<StaffListItemDto> {
        enforceOrgAdmin(actorId, organizationId)
        val members = staffRepository.findAllByOrganizationId(organizationId, pageable)
        val venueMap = resolveVenues(members.content)
        val data = members.content.map { toListItem(it, venueMap) }
        return PageImpl(data, pageable, members.totalElements)
    }

    @Transactional(readOnly = true)
    fun listVenuePermissions(
        actorId: UUID,
        venueId: UUID,
        pageable: Pageable
    ): Page<VenuePermissionDto> {
        val orgId = venueApi.getVenueOrganizationId(venueId)
            ?: throw VenuesException.ResourceNotFound("Venue not found", "VENUE_NOT_FOUND")
        enforceOrgAdmin(actorId, orgId)

        val permissionsPage = venuePermissionRepository.findByVenueId(venueId, pageable)
        val data = permissionsPage.content.map { perm ->
            val staff = perm.membership.staff
            VenuePermissionDto(
                staffId = staff.id!!,
                staffEmail = staff.email,
                role = perm.role
            )
        }
        return PageImpl(data, pageable, permissionsPage.totalElements)
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

    private fun upsertMembership(
        staff: StaffIdentity,
        organizationId: UUID,
        orgRole: OrganizationRole
    ): StaffMembership {
        val existing = staff.memberships.firstOrNull { it.organizationId == organizationId }
        return if (existing != null) {
            existing.orgRole = orgRole
            existing.isActive = true
            existing
        } else {
            StaffMembership(
                staff = staff,
                organizationId = organizationId,
                orgRole = orgRole,
                isActive = true
            ).also { staff.memberships.add(it) }
        }
    }

    private fun applyVenuePermissions(
        membership: StaffMembership,
        permissions: List<VenuePermissionInput>
    ) {
        permissions.forEach { perm ->
            val existingPerm = membership.venuePermissions.firstOrNull { it.venueId == perm.venueId }
            if (existingPerm != null) {
                existingPerm.role = perm.role
            } else {
                membership.venuePermissions.add(
                    StaffVenuePermission(
                        membership = membership,
                        venueId = perm.venueId,
                        role = perm.role
                    )
                )
            }
        }
    }

    private fun validateVenueOwnership(organizationId: UUID, permissions: List<VenuePermissionInput>) {
        permissions.forEach { perm ->
            val venueOrg = venueApi.getVenueOrganizationId(perm.venueId)
                ?: throw VenuesException.ResourceNotFound("Venue not found", "VENUE_NOT_FOUND")
            if (venueOrg != organizationId) {
                throw VenuesException.AuthorizationFailure("Venue ${perm.venueId} does not belong to organization $organizationId")
            }
        }
    }

    private fun sendInvitationEmail(staff: StaffIdentity) {
        try {
            val token = staff.verificationToken ?: return
            val inviteUrl = "${frontendBaseUrl.trimEnd('/')}/staff/accept-invite?token=$token"
            val content = emailTemplateService.generateStaffVerificationEmail(
                name = listOfNotNull(staff.firstName, staff.lastName).joinToString(" ").ifBlank { "there" },
                verificationUrl = inviteUrl
            )
            emailService.sendGlobalEmail(
                to = staff.email,
                subject = "You're invited to manage venues",
                content = content,
                isHtml = true
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to send invitation email to ${staff.email}" }
        }
    }

    private fun sendAccountReadyEmail(staff: StaffIdentity) {
        try {
            val content = """
                <p>Your staff account is ready.</p>
                <p>You can sign in with your email at ${frontendBaseUrl.trimEnd('/')}/staff/login</p>
            """.trimIndent()
            emailService.sendGlobalEmail(
                to = staff.email,
                subject = "Your staff account is ready",
                content = content,
                isHtml = true
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to send account ready email to ${staff.email}" }
        }
    }

    /**
     * Detailed profile for admin views.
     */
    @Transactional(readOnly = true)
    fun getStaffDetail(actorId: UUID, staffId: UUID): StaffDetailDto {
        val actor = staffRepository.findById(actorId).orElseThrow {
            VenuesException.AuthorizationFailure("Not authorized")
        }
        val staff = staffRepository.findById(staffId).orElseThrow {
            VenuesException.ResourceNotFound("Staff not found", "STAFF_NOT_FOUND")
        }

        authorizeActorForStaff(actor, staff)

        return toDetailDto(staff)
    }

    /**
     * Update org membership role/active flag.
     */
    @Transactional
    fun updateMembership(
        actorId: UUID,
        staffId: UUID,
        organizationId: UUID,
        request: UpdateMembershipRequest
    ): StaffDetailDto {
        enforceOrgAdmin(actorId, organizationId)

        val staff = staffRepository.findById(staffId).orElseThrow {
            VenuesException.ResourceNotFound("Staff not found", "STAFF_NOT_FOUND")
        }

        val membership = upsertMembership(staff, organizationId, request.role)
        membership.isActive = request.isActive

        val saved = staffRepository.save(staff)
        return toDetailDto(saved)
    }

    /**
     * Remove org membership.
     */
    @Transactional
    fun deleteMembership(
        actorId: UUID,
        staffId: UUID,
        organizationId: UUID
    ): StaffDetailDto {
        enforceOrgAdmin(actorId, organizationId)

        val staff = staffRepository.findById(staffId).orElseThrow {
            VenuesException.ResourceNotFound("Staff not found", "STAFF_NOT_FOUND")
        }

        val membership = staff.memberships.firstOrNull { it.organizationId == organizationId }
            ?: throw VenuesException.ResourceNotFound("Membership not found", "MEMBERSHIP_NOT_FOUND")

        staff.memberships.remove(membership)
        val saved = staffRepository.save(staff)
        return toDetailDto(saved)
    }

    /**
     * Update venue role (idempotent set).
     */
    @Transactional
    fun updateVenueRole(
        actorId: UUID,
        staffId: UUID,
        venueId: UUID,
        request: UpdateVenueRoleRequest
    ): StaffDetailDto {
        val venueOrgId = venueApi.getVenueOrganizationId(venueId)
            ?: throw VenuesException.ResourceNotFound("Venue not found", "VENUE_NOT_FOUND")

        enforceOrgAdmin(actorId, venueOrgId)

        val staff = staffRepository.findById(staffId).orElseThrow {
            VenuesException.ResourceNotFound("Staff not found", "STAFF_NOT_FOUND")
        }

        val membership = staff.memberships.firstOrNull { it.organizationId == venueOrgId }
            ?: StaffMembership(
                staff = staff,
                organizationId = venueOrgId,
                orgRole = OrganizationRole.MEMBER,
                isActive = true
            ).also { staff.memberships.add(it) }

        val existingPerm = membership.venuePermissions.firstOrNull { it.venueId == venueId }
        if (existingPerm != null) {
            existingPerm.role = request.role
        } else {
            membership.venuePermissions.add(
                StaffVenuePermission(
                    membership = membership,
                    venueId = venueId,
                    role = request.role
                )
            )
        }

        val saved = staffRepository.save(staff)
        return toDetailDto(saved)
    }

    private fun authorizeActorForStaff(actor: StaffIdentity, target: StaffIdentity) {
        if (actor.isPlatformSuperAdmin) return
        val targetOrgIds = target.memberships.map { it.organizationId }.toSet()
        val actorAdminOrgIds = actor.memberships
            .filter { it.isActive && it.orgRole in listOf(OrganizationRole.OWNER, OrganizationRole.ADMIN) }
            .map { it.organizationId }
            .toSet()
        if (targetOrgIds.intersect(actorAdminOrgIds).isEmpty()) {
            throw VenuesException.AuthorizationFailure("Not authorized to view this staff")
        }
    }

    private fun toDetailDto(staff: StaffIdentity): StaffDetailDto {
        val organizations = staff.memberships
            .filter { it.isActive }
            .map { OrganizationAccessDto(id = it.organizationId, role = it.orgRole) }
        val venueMap = resolveVenues(listOf(staff))
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
        return StaffDetailDto(
            id = staff.id!!,
            email = staff.email,
            firstName = staff.firstName,
            lastName = staff.lastName,
            status = staff.status,
            isSuperAdmin = staff.isPlatformSuperAdmin,
            organizations = organizations,
            venueRoles = venues
        )
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

}
