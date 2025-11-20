package app.venues.staff.api.mapper

import app.venues.staff.api.dto.OrganizationMembershipDto
import app.venues.staff.api.dto.StaffGlobalContextDto
import app.venues.staff.api.dto.StaffProfileDto
import app.venues.staff.api.dto.VenuePermissionDto
import app.venues.staff.domain.StaffIdentity
import app.venues.staff.domain.StaffMembership
import app.venues.staff.domain.StaffVenuePermission
import org.springframework.stereotype.Component

@Component
class StaffMapper {

    fun toProfileDto(entity: StaffIdentity): StaffProfileDto {
        return StaffProfileDto(
            id = entity.id,
            email = entity.email,
            firstName = entity.firstName,
            lastName = entity.lastName,
            status = entity.status,
            isPlatformSuperAdmin = entity.isPlatformSuperAdmin
        )
    }

    fun toGlobalContextDto(entity: StaffIdentity): StaffGlobalContextDto {
        // Filter only active memberships
        val activeMemberships = entity.memberships
            .filter { it.isActive }
            .map { toMembershipDto(it) }

        return StaffGlobalContextDto(memberships = activeMemberships)
    }

    private fun toMembershipDto(membership: StaffMembership): OrganizationMembershipDto {
        return OrganizationMembershipDto(
            organizationId = membership.organizationId,
            orgRole = membership.orgRole,
            venuePermissions = membership.venuePermissions.map { toVenuePermissionDto(it) }
        )
    }

    private fun toVenuePermissionDto(permission: StaffVenuePermission): VenuePermissionDto {
        return VenuePermissionDto(
            venueId = permission.venueId,
            role = permission.role
        )
    }
}