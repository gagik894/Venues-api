package app.venues.venue.api.dto

import java.util.*

data class VenueBrandingDto(
    val venueId: UUID,
    val primaryColor: String?,
    val secondaryColor: String?,
    val faviconUrl: String?,
    val homeHero: HeroConfigDto?,
    val aboutBlocks: List<ContentBlockDto>?,
    val contactConfig: ContactConfigDto?,
    val venueName: String,
    val logoUrl: String?,
    val coverImageUrl: String?,
    val socialLinks: Map<String, String>?,
    val contactEmail: String?,
    val phoneNumber: String?,
    val address: String?,
    val website: String?,
    val latitude: Double?,
    val longitude: Double?
)

data class HeroConfigDto(
    val title: Map<String, String>, //language code -> title
    val imageUrl: String,
    val subtitle: Map<String, String>?,
    val ctaText: Map<String, String>?,
    val ctaLink: String?
)

enum class ContentBlockType {
    TEXT,
    IMAGE,
    IMAGE_RIGHT,
    IMAGE_LEFT,
    CTA,
    FAQ
}

data class ContentBlockDto(
    val type: ContentBlockType,
    val title: Map<String, String>?,
    val body: Map<String, String>?, //language code -> body content
    val imageUrl: String?
)

data class ContactConfigDto(
    val mapUrl: String?,
    val showForm: Boolean = true
)

data class UpdateVenueBrandingRequest(
    val primaryColor: String?,
    val secondaryColor: String?,
    val faviconUrl: String?,
    val homeHero: HeroConfigDto?,
    val aboutBlocks: List<ContentBlockDto>?,
    val contactConfig: ContactConfigDto?,
    val venueName: String? = null,
    val logoUrl: String? = null,
    val coverImageUrl: String? = null,
    val socialLinks: Map<String, String>? = null, //platform -> link
    val contactEmail: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val website: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
