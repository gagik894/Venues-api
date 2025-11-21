package app.venues.venue.service

import app.venues.common.constants.AppConstants
import app.venues.common.exception.VenuesException
import app.venues.staff.api.StaffSecurityFacade
import app.venues.venue.api.VenueApi
import app.venues.venue.api.service.VenueSecurityService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.*

@Service
class VenueSecurityServiceImpl(
    private val venueApi: VenueApi,
    private val staffSecurityFacade: StaffSecurityFacade
) : VenueSecurityService {
    private val logger = KotlinLogging.logger {}

    /**
     * Checks if staff has permission to manage the specified venue.
     *
     * Uses StaffSecurityFacade which checks:
     * 1. Is staff a SUPER_ADMIN? → Allow
     * 2. Is staff OWNER/ADMIN of venue's organization? → Allow
     * 3. Does staff have explicit venue MANAGER permission? → Allow
     * 4. Otherwise → Deny
     *
     * @param staffId Staff UUID from JWT
     * @param venueId Venue UUID from path
     * @throws VenuesException.AuthorizationFailure if staff cannot manage venue
     */
    override fun requireVenueManagementPermission(staffId: UUID, venueId: UUID) {
        // Get venue's organization ID (needed for permission check)
        val organizationId = venueApi.getVenueOrganizationId(venueId)
            ?: throw VenuesException.ResourceNotFound(
                "Venue not found or has no organization",
                AppConstants.ErrorCode.NOT_FOUND.code
            )

        // Check permission via facade
        if (!staffSecurityFacade.canManageVenue(staffId, venueId, organizationId)) {
            logger.warn { "Staff $staffId attempted to manage venue $venueId without permission" }
            throw VenuesException.AuthorizationFailure(
                "You don't have permission to manage this venue",
                AppConstants.ErrorCode.AUTHORIZATION_FAILED.code
            )
        }
    }
}
