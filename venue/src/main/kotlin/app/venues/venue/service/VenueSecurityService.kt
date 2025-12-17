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
     */
    override fun requireVenueManagementPermission(staffId: UUID, venueId: UUID) {
        requirePermission(
            staffId = staffId,
            venueId = venueId,
            action = "manage",
            checker = staffSecurityFacade::canManageVenue
        )
    }

    /**
     * Checks if staff has permission to edit the specified venue.
     */
    override fun requireVenueEditPermission(staffId: UUID, venueId: UUID) {
        requirePermission(
            staffId = staffId,
            venueId = venueId,
            action = "edit",
            checker = staffSecurityFacade::canEditVenue
        )
    }

    /**
     * Checks if staff has permission to sell for the specified venue.
     */
    override fun requireVenueSellPermission(staffId: UUID, venueId: UUID) {
        requirePermission(
            staffId = staffId,
            venueId = venueId,
            action = "sell for",
            checker = staffSecurityFacade::canSellAtVenue
        )
    }

    /**
     * Checks if staff has permission to scan tickets for the specified venue.
     */
    override fun requireVenueScanPermission(staffId: UUID, venueId: UUID) {
        requirePermission(
            staffId = staffId,
            venueId = venueId,
            action = "scan at",
            checker = staffSecurityFacade::canScanAtVenue
        )
    }

    /**
     * Checks if staff has permission to view the specified venue.
     */
    override fun requireVenueViewPermission(staffId: UUID, venueId: UUID) {
        requirePermission(
            staffId = staffId,
            venueId = venueId,
            action = "view",
            checker = staffSecurityFacade::canViewVenue
        )
    }

    /**
     * Checks if staff can browse (events list/details) for the specified venue.
     */
    override fun requireVenueBrowsePermission(staffId: UUID, venueId: UUID) {
        requirePermission(
            staffId = staffId,
            venueId = venueId,
            action = "browse",
            checker = staffSecurityFacade::canBrowseVenue
        )
    }

    /**
     * Shared helper to resolve venue organization and enforce permission via StaffSecurityFacade.
     */
    private fun requirePermission(
        staffId: UUID,
        venueId: UUID,
        action: String,
        checker: (UUID, UUID, UUID) -> Boolean
    ) {
        val organizationId = venueApi.getVenueOrganizationId(venueId)
            ?: throw VenuesException.ResourceNotFound(
                "Venue not found or has no organization",
                AppConstants.ErrorCode.NOT_FOUND.code
            )

        if (!checker(staffId, venueId, organizationId)) {
            logger.warn { "Staff $staffId attempted to $action venue $venueId without permission" }
            throw VenuesException.AuthorizationFailure(
                "You don't have permission to $action this venue",
                AppConstants.ErrorCode.AUTHORIZATION_FAILED.code
            )
        }
    }
}
