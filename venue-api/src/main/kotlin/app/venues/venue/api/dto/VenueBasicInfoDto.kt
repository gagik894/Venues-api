package app.venues.venue.api.dto

import java.util.*

/**
 * Basic venue information DTO for cross-module communication.
 */
data class VenueBasicInfoDto(
    val id: UUID,
    val name: String,
    val slug: String, // Added slug
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val organizationId: UUID,
    val merchantProfileId: UUID?
)

