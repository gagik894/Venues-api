package app.venues.venue.api.dto

/**
 * Fully localized website data for a specific language.
 * The frontend can render this directly without any translation logic.
 */
data class LocalizedVenueWebsiteDto(
    val language: String,
    val venue: VenueDetailResponse,
    val branding: LocalizedVenueBrandingDto?,
    val photos: List<VenuePhotoResponse>
)

data class LocalizedVenueBrandingDto(
    val primaryColor: String?,
    val secondaryColor: String?,
    val faviconUrl: String?,
    val homeHero: LocalizedHeroConfigDto?,
    val aboutBlocks: List<LocalizedContentBlockDto>?,
    val contactConfig: LocalizedContactConfigDto?
)

data class LocalizedHeroConfigDto(
    val title: String,
    val subtitle: String?,
    val ctaText: String?,
    val ctaLink: String?
)

data class LocalizedContentBlockDto(
    val type: String,
    val title: String?,
    val body: String?,
    val imageUrl: String?
)

data class LocalizedContactConfigDto(
    val mapUrl: String?,
    val showForm: Boolean
)
