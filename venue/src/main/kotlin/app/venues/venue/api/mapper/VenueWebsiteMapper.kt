package app.venues.venue.api.mapper

import app.venues.venue.api.dto.ContactConfigDto
import app.venues.venue.api.dto.ContentBlockDto
import app.venues.venue.api.dto.HeroConfigDto
import app.venues.venue.api.dto.VenueBrandingDto
import app.venues.venue.domain.ContactConfig
import app.venues.venue.domain.ContentBlock
import app.venues.venue.domain.HeroConfig
import app.venues.venue.domain.VenueBranding
import org.springframework.stereotype.Component

@Component
class VenueWebsiteMapper {

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
        content = domain.content,
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
        content = dto.content,
        imageUrl = dto.imageUrl
    )

    fun toDomain(dto: ContactConfigDto): ContactConfig = ContactConfig(
        mapUrl = dto.mapUrl,
        showForm = dto.showForm
    )
}