package app.venues.venue.api.dto

/**
 * Basic venue information DTO for cross-module communication.
 */
data class VenueBasicInfoDto(
    val id: Long,
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?
)

