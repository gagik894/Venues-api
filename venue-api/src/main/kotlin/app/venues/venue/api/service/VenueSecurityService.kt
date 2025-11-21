package app.venues.venue.api.service

import java.util.*

interface VenueSecurityService {
    fun requireVenueManagementPermission(staffId: UUID, venueId: UUID)
}