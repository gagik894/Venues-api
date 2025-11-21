package app.venues.venue.api.dto

/**
 * Aggregated data for rendering a white-label venue website.
 */
data class VenueWebsiteDataDto(
    val venue: VenueDetailResponse,
    val branding: VenueBrandingDto?,
    val photos: List<VenuePhotoResponse>
)
