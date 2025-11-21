package app.venues.venue.api.mapper

import app.venues.venue.api.dto.*
import app.venues.venue.domain.*
import org.springframework.stereotype.Component

@Component
class VenueWebsiteMapper(
    private val venueMapper: VenueMapper
) {

    fun toLocalizedWebsiteData(venue: Venue, lang: String): LocalizedVenueWebsiteDto {
        return LocalizedVenueWebsiteDto(
            language = lang,
            venue = venueMapper.toDetailResponse(venue, lang),
            branding = venue.branding?.let { toLocalizedDto(it, lang) },
            photos = venue.photos.map { venueMapper.toPhotoResponse(it) }
        )
    }

    fun toLocalizedDto(domain: VenueBranding, lang: String): LocalizedVenueBrandingDto {
        return LocalizedVenueBrandingDto(
            primaryColor = domain.primaryColor,
            secondaryColor = domain.secondaryColor,
            faviconUrl = domain.faviconUrl,
            homeHero = domain.homeHero?.let { toLocalizedDto(it, lang) },
            aboutBlocks = domain.aboutBlocks?.map { toLocalizedDto(it, lang) },
            contactConfig = domain.contactConfig?.let { toLocalizedDto(it) }
        )
    }

    private fun toLocalizedDto(domain: HeroConfig, lang: String): LocalizedHeroConfigDto {
        return LocalizedHeroConfigDto(
            title = resolveString(domain.title, lang),
            subtitle = domain.subtitle?.let { resolveString(it, lang) },
            ctaText = domain.ctaText?.let { resolveString(it, lang) },
            ctaLink = domain.ctaLink
        )
    }

    private fun toLocalizedDto(domain: ContentBlock, lang: String): LocalizedContentBlockDto {
        return LocalizedContentBlockDto(
            type = domain.type,
            title = domain.title?.let { resolveString(it, lang) },
            body = domain.body?.let { resolveString(it, lang) },
            imageUrl = domain.imageUrl
        )
    }

    private fun toLocalizedDto(domain: ContactConfig): LocalizedContactConfigDto {
        return LocalizedContactConfigDto(
            mapUrl = domain.mapUrl,
            showForm = domain.showForm
        )
    }

    // --- Helpers ---

    private fun resolveString(localizedMap: Map<String, String>, lang: String): String {
        return localizedMap[lang] ?: localizedMap["en"] ?: localizedMap.values.firstOrNull() ?: ""
    }

    // --- Existing Methods (kept for Admin API) ---

    fun toWebsiteData(venue: Venue): VenueWebsiteDataDto {
        return VenueWebsiteDataDto(
            venue = venueMapper.toDetailResponse(venue),
            branding = venue.branding?.let { toDto(it) },
            photos = venue.photos.map { venueMapper.toPhotoResponse(it) }
        )
    }

    fun toDto(domain: VenueBranding): VenueBrandingDto {
        return VenueBrandingDto(
            venueId = domain.id!!,
            primaryColor = domain.primaryColor,
            secondaryColor = domain.secondaryColor,
            faviconUrl = domain.faviconUrl,
            homeHero = domain.homeHero?.let { toDto(it) },
            aboutBlocks = domain.aboutBlocks?.map { toDto(it) },
            contactConfig = domain.contactConfig?.let { toDto(it) }
        )
    }

    fun toDto(domain: HeroConfig): HeroConfigDto = HeroConfigDto(
        title = domain.title,
        subtitle = domain.subtitle,
        ctaText = domain.ctaText,
        ctaLink = domain.ctaLink
    )

    fun toDto(domain: ContentBlock): ContentBlockDto = ContentBlockDto(
        type = domain.type,
        title = domain.title,
        body = domain.body,
        imageUrl = domain.imageUrl
    )

    fun toDto(domain: ContactConfig): ContactConfigDto = ContactConfigDto(
        mapUrl = domain.mapUrl,
        showForm = domain.showForm
    )

    fun toDomain(dto: HeroConfigDto): HeroConfig = HeroConfig(
        title = dto.title,
        subtitle = dto.subtitle,
        ctaText = dto.ctaText,
        ctaLink = dto.ctaLink
    )

    fun toDomain(dto: ContentBlockDto): ContentBlock = ContentBlock(
        type = dto.type,
        title = dto.title,
        body = dto.body,
        imageUrl = dto.imageUrl
    )

    fun toDomain(dto: ContactConfigDto): ContactConfig = ContactConfig(
        mapUrl = dto.mapUrl,
        showForm = dto.showForm
    )
}