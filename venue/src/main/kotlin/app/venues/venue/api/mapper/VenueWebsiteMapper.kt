package app.venues.venue.api.mapper

import app.venues.venue.api.dto.*
import app.venues.venue.domain.*
import org.springframework.stereotype.Component

@Component
class VenueWebsiteMapper(
    private val venueMapper: VenueMapper
) {

    // ===========================================
    // LAYOUT MAPPING
    // ===========================================

    fun toLayoutDto(venue: Venue, lang: String): WebsiteLayoutDto {
        val branding = venue.branding
        return WebsiteLayoutDto(
            language = lang,
            theme = WebsiteThemeDto(
                primaryColor = branding?.primaryColor,
                secondaryColor = branding?.secondaryColor,
                faviconUrl = branding?.faviconUrl
            ),
            header = WebsiteHeaderDto(
                venueName = venue.getName(lang),
                logoUrl = venue.logoUrl,
                coverImageUrl = venue.coverImageUrl
            ),
            footer = WebsiteFooterDto(
                socialLinks = venue.socialLinks,
                contactEmail = venue.contactEmail,
                phoneNumber = venue.phoneNumber,
                address = venue.address,
                city = venue.city.getName(lang)
            )
        )
    }

    // ===========================================
    // PAGE MAPPING
    // ===========================================

    fun toHomePageDto(venue: Venue, lang: String): HomePageDto {
        return HomePageDto(
            hero = venue.branding?.homeHero?.let { toLocalizedDto(it, lang) }
        )
    }

    fun toAboutPageDto(venue: Venue, lang: String): AboutPageDto {
        return AboutPageDto(
            blocks = venue.branding?.aboutBlocks?.map { toLocalizedDto(it, lang) } ?: emptyList()
        )
    }

    fun toContactPageDto(venue: Venue, lang: String): ContactPageDto {
        return ContactPageDto(
            address = venue.address,
            city = venue.city.getName(lang),
            coordinates = CoordinatesDto(venue.latitude, venue.longitude),
            contactInfo = ContactInfoDto(
                phone = venue.phoneNumber,
                email = venue.contactEmail,
                website = venue.website
            ),
            schedule = venue.schedules.map { venueMapper.toScheduleDto(it) },
            mapConfig = venue.branding?.contactConfig?.let { toLocalizedDto(it) }
        )
    }

    // ===========================================
    // COMPONENT MAPPING
    // ===========================================

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
        ctaLink = domain.ctaLink,
        imageUrl = domain.imageUrl
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
        ctaLink = dto.ctaLink,
        imageUrl = dto.imageUrl
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

    private fun toLocalizedDto(domain: HeroConfig, lang: String): LocalizedHeroConfigDto {
        return LocalizedHeroConfigDto(
            title = resolveString(domain.title, lang),
            subtitle = domain.subtitle?.let { resolveString(it, lang) },
            ctaText = domain.ctaText?.let { resolveString(it, lang) },
            ctaLink = domain.ctaLink,
            imageUrl = domain.imageUrl
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
}