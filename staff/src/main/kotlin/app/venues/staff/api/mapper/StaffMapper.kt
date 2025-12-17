package app.venues.staff.api.mapper

import app.venues.staff.api.dto.AuthorizedVenueDto
import app.venues.staff.api.dto.OrganizationAccessDto
import app.venues.staff.api.dto.StaffAuthResponse
import app.venues.staff.api.dto.StaffProfileDto
import app.venues.staff.domain.StaffIdentity

/**
 * Mapper object for converting between StaffIdentity entities and DTOs.
 *
 * Responsibilities:
 * - Convert entities to DTOs for API responses
 * - Ensure sensitive data (passwords, tokens) never leaves the domain layer
 * - Provide consistent transformation logic
 *
 * Design Pattern: Mapper Pattern
 * - Separates domain entities from API contracts
 * - Allows evolution of internal model without breaking API
 * - Centralizes conversion logic
 */
object StaffMapper {

    /**
     * Converts a StaffIdentity entity to StaffProfileDto.
     *
     * Filters out sensitive information:
     * - Password hash
     * - Failed login attempts
     * - Lock information
     * - Verification tokens
     *
     * @param staff The StaffIdentity entity to convert
     * @return StaffProfileDto safe for API responses
     */
    fun toProfileDto(staff: StaffIdentity): StaffProfileDto {
        return StaffProfileDto(
            id = staff.id,
            email = staff.email,
            firstName = staff.firstName ?: "",
            lastName = staff.lastName ?: ""
        )
    }

    /**
     * Converts staff identity, authorized venues, and token into complete auth response.
     *
     * Used for login and registration responses.
     *
     * @param staff The authenticated staff identity
     * @param authorizedVenues List of venues the staff member can access with their roles
     * @param token The JWT token
     * @param expiresInSeconds Token expiration time in seconds
     * @return Complete authentication response
     */
    fun toAuthResponse(
        staff: StaffIdentity,
        authorizedVenues: List<AuthorizedVenueDto>,
        token: String,
        expiresInSeconds: Long
    ): StaffAuthResponse {
        return StaffAuthResponse(
            token = token,
            expiresIn = expiresInSeconds,
            profile = toProfileDto(staff),
            authorizedVenues = authorizedVenues,
            organizations = staff.memberships
                .filter { it.isActive }
                .map { OrganizationAccessDto(id = it.organizationId, role = it.orgRole) },
            isSuperAdmin = staff.isPlatformSuperAdmin
        )
    }
}