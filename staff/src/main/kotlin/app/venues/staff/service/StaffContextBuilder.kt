package app.venues.staff.service

import app.venues.common.exception.VenuesException
import app.venues.staff.api.dto.AuthorizedVenueDto
import app.venues.staff.domain.StaffIdentity
import app.venues.staff.repository.StaffIdentityRepository
import app.venues.venue.api.VenueApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Builds the staff authorized venues list.
 *
 * This service constructs a flat list of venues the staff member can access
 * along with their assigned role for each venue.
 *
 * Used by:
 * - Auth services to build context during login/registration
 * - Management services to refresh context after membership changes
 * - Frontend to build navigation menus and permission checks
 */
@Service
@Transactional(readOnly = true)
class StaffContextBuilder(
    private val staffRepository: StaffIdentityRepository,
    private val venueApi: VenueApi
) {

    /**
     * Builds the complete list of authorized venues for a staff member.
     *
     * Returns:
     * - All venues they have access to across all organizations
     * - Their specific role for each venue (MANAGER, SCANNER, etc.)
     * - Venue basic information (ID, name)
     *
     * @param staff The staff identity entity (must be managed)
     * @return List of authorized venues with roles
     */
    fun buildAuthorizedVenues(staff: StaffIdentity): List<AuthorizedVenueDto> {
        val activePermissions = staff.memberships
            .asSequence()
            .filter { it.isActive }
            .flatMap { it.venuePermissions.asSequence() }
            .toList()

        if (activePermissions.isEmpty()) {
            return emptyList()
        }

        val venueInfos = venueApi.getVenueBasicInfoBatch(activePermissions.map { it.venueId }.toSet())

        return activePermissions
            .mapNotNull { permission ->
                val venueInfo = venueInfos[permission.venueId]
                venueInfo?.let {
                    AuthorizedVenueDto(
                        id = it.id,
                        name = it.name,
                        slug = it.slug,
                        role = permission.role
                    )
                }
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Builds authorized venues list by staff ID.
     *
     * Loads the staff entity from database and builds the authorized venues list.
     *
     * @param staffId Staff member UUID
     * @return List of authorized venues with roles
     * @throws VenuesException.ResourceNotFound if staff not found
     */
    fun buildAuthorizedVenuesById(staffId: UUID): List<AuthorizedVenueDto> {
        val staff = staffRepository.findById(staffId)
            .orElseThrow {
                VenuesException.ResourceNotFound(
                    "Staff not found",
                    "STAFF_NOT_FOUND"
                )
            }
        return buildAuthorizedVenues(staff)
    }
}
