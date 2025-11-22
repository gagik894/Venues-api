package app.venues.staff.api.dto

import app.venues.staff.domain.VenueRole
import java.util.*

/**
 * Basic staff profile information.
 *
 * Contains identifying information and name fields only.
 */
data class StaffProfileDto(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?
)

/**
 * Authorized venue access entry.
 *
 * Represents a single venue the staff member can access with their assigned role.
 */
data class AuthorizedVenueDto(
    val id: UUID,
    val name: String,
    val role: VenueRole
)

/**
 * Response DTO for successful staff authentication.
 *
 * Contains JWT token, basic profile, and flat list of authorized venues with roles.
 */
data class StaffAuthResponse(
    val token: String,
    val expiresIn: Long,
    val profile: StaffProfileDto,
    val authorizedVenues: List<AuthorizedVenueDto>
)