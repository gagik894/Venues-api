package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.venue.api.dto.*
import app.venues.venue.api.mapper.VenueWebsiteMapper
import app.venues.venue.domain.VenueBranding
import app.venues.venue.repository.VenueBrandingRepository
import app.venues.venue.repository.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class VenueWebsiteService(
    private val venueRepository: VenueRepository,
    private val venueBrandingRepository: VenueBrandingRepository,
    private val venueWebsiteMapper: VenueWebsiteMapper,
    private val venueRevalidationService: VenueRevalidationService
) {

    @Transactional(readOnly = true)
    fun getVenueBranding(venueId: UUID): VenueBrandingDto {
        val branding = venueBrandingRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Branding not found for venue $venueId") }

        return venueWebsiteMapper.toDto(branding)
    }

    @Transactional(readOnly = true)
    fun getWebsiteLayout(venueId: UUID, lang: String): WebsiteLayoutDto {
        val venue = getVenueOrThrow(venueId)
        return venueWebsiteMapper.toLayoutDto(venue, lang)
    }

    @Transactional(readOnly = true)
    fun getHomePage(venueId: UUID, lang: String): HomePageDto {
        val venue = getVenueOrThrow(venueId)
        return venueWebsiteMapper.toHomePageDto(venue, lang)
    }

    @Transactional(readOnly = true)
    fun getAboutPage(venueId: UUID, lang: String): AboutPageDto {
        val venue = getVenueOrThrow(venueId)
        return venueWebsiteMapper.toAboutPageDto(venue, lang)
    }

    @Transactional(readOnly = true)
    fun getContactPage(venueId: UUID, lang: String): ContactPageDto {
        val venue = getVenueOrThrow(venueId)
        return venueWebsiteMapper.toContactPageDto(venue, lang)
    }

    fun updateVenueBranding(venueId: UUID, request: UpdateVenueBrandingRequest): VenueBrandingDto {
        val venue = getVenueOrThrow(venueId)

        // Use existing branding or create new one. Do NOT set id manually with @MapsId.
        val branding = venueBrandingRepository.findById(venueId)
            .orElseGet { VenueBranding(venue = venue) }

        branding.primaryColor = request.primaryColor
        branding.secondaryColor = request.secondaryColor
        branding.faviconUrl = request.faviconUrl
        branding.homeHero = request.homeHero?.let { venueWebsiteMapper.toDomain(it) }
        branding.aboutBlocks = request.aboutBlocks?.map { venueWebsiteMapper.toDomain(it) }
        branding.contactConfig = request.contactConfig?.let { venueWebsiteMapper.toDomain(it) }

        val saved = venueBrandingRepository.save(branding)
        venueRevalidationService.revalidate(venue, reason = "venue-branding-updated")
        return venueWebsiteMapper.toDto(saved)
    }

    private fun getVenueOrThrow(venueId: UUID) = venueRepository.findById(venueId)
        .orElseThrow { VenuesException.ResourceNotFound("Venue not found: $venueId") }
}
