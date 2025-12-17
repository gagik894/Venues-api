package app.venues.venue.api.dto

// ===========================================
// LAYOUT (Header, Footer, Theme)
// ===========================================

data class WebsiteLayoutDto(
    val language: String,
    val theme: WebsiteThemeDto,
    val header: WebsiteHeaderDto,
    val footer: WebsiteFooterDto
)

data class WebsiteThemeDto(
    val primaryColor: String?,
    val secondaryColor: String?,
    val faviconUrl: String?
)

data class WebsiteHeaderDto(
    val venueName: String,
    val logoUrl: String?,
    val coverImageUrl: String?
)

data class WebsiteFooterDto(
    val socialLinks: Map<String, String>?,
    val contactEmail: String?,
    val phoneNumber: String?,
    val address: String,
    val city: String
)

// ===========================================
// PAGES
// ===========================================

data class HomePageDto(
    val hero: LocalizedHeroConfigDto?
)

data class AboutPageDto(
    val blocks: List<LocalizedContentBlockDto>
)

data class ContactPageDto(
    val address: String,
    val city: String,
    val coordinates: CoordinatesDto?,
    val contactInfo: ContactInfoDto,
    val schedule: List<VenueScheduleDto>,
    val mapConfig: LocalizedContactConfigDto?
)

data class CoordinatesDto(val lat: Double?, val lng: Double?)
data class ContactInfoDto(val phone: String?, val email: String?, val website: String?)

// ===========================================
// COMPONENTS
// ===========================================

data class LocalizedHeroConfigDto(
    val title: String,
    val imageUrl: String,
    val subtitle: String?,
    val ctaText: String?,
    val ctaLink: String?
)

data class LocalizedContentBlockDto(
    val type: String, // "text", "image", "image_right", "image_left"
    val title: String?,
    val body: String?,
    val imageUrl: String?
)

data class LocalizedContactConfigDto(
    val mapUrl: String?,
    val showForm: Boolean
)

