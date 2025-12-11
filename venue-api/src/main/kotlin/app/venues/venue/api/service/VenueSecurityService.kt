package app.venues.venue.api.service

import java.util.*

interface VenueSecurityService {
    /**
     * Throws if staff cannot manage the venue (create/update venue-level resources).
     */
    fun requireVenueManagementPermission(staffId: UUID, venueId: UUID)

    /**
     * Throws if staff cannot edit venue content (events, details, etc.).
     */
    fun requireVenueEditPermission(staffId: UUID, venueId: UUID)

    /**
     * Throws if staff cannot sell tickets/operate box office for the venue.
     */
    fun requireVenueSellPermission(staffId: UUID, venueId: UUID)

    /**
     * Throws if staff cannot scan tickets for the venue.
     */
    fun requireVenueScanPermission(staffId: UUID, venueId: UUID)

    /**
     * Throws if staff cannot view the venue (read-only).
     */
    fun requireVenueViewPermission(staffId: UUID, venueId: UUID)
}