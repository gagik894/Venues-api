package app.venues.venue.api.dto

import java.util.*

data class VenueBrandingDto(
    val venueId: UUID,
    val primaryColor: String?,
    val secondaryColor: String?,
    val faviconUrl: String?,
    val homeHero: HeroConfigDto?,
    val aboutBlocks: List<ContentBlockDto>?,
    val contactConfig: ContactConfigDto?
)

data class HeroConfigDto(
    val title: Map<String, String>,
    val subtitle: Map<String, String>?,
    val ctaText: Map<String, String>?,
    val ctaLink: String?
)

data class ContentBlockDto(
    val type: String,
    val content: Map<String, String>?,
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
    val contactConfig: ContactConfigDto?
)
