package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.venue.api.dto.LocalizedVenueWebsiteDto
import app.venues.venue.api.dto.UpdateVenueBrandingRequest
import app.venues.venue.api.dto.VenueBrandingDto
import app.venues.venue.api.dto.VenueWebsiteDataDto
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
    private val venueWebsiteMapper: VenueWebsiteMapper
) {

    @Transactional(readOnly = true)
    fun getVenueBranding(venueId: UUID): VenueBrandingDto {
        val branding = venueBrandingRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Branding not found for venue $venueId") }

        return venueWebsiteMapper.toDto(branding)
    }

    @Transactional(readOnly = true)
    fun getVenueWebsiteData(venueId: UUID): VenueWebsiteDataDto {
        val venue = venueRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Venue not found: $venueId") }

        return venueWebsiteMapper.toWebsiteData(venue)
    }

    @Transactional(readOnly = true)
    fun getLocalizedVenueWebsiteData(venueId: UUID, lang: String): LocalizedVenueWebsiteDto {
        val venue = venueRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Venue not found: $venueId") }

        return venueWebsiteMapper.toLocalizedWebsiteData(venue, lang)
    }

    fun updateVenueBranding(venueId: UUID, request: UpdateVenueBrandingRequest): VenueBrandingDto {
        val venue = venueRepository.findById(venueId)
            .orElseThrow { VenuesException.ResourceNotFound("Venue not found: $venueId") }

        val branding = venueBrandingRepository.findById(venueId)
            .orElse(VenueBranding(id = venueId, venue = venue))

        branding.primaryColor = request.primaryColor
        branding.secondaryColor = request.secondaryColor
        branding.faviconUrl = request.faviconUrl
        branding.homeHero = request.homeHero?.let { venueWebsiteMapper.toDomain(it) }
        branding.aboutBlocks = request.aboutBlocks?.map { venueWebsiteMapper.toDomain(it) }
        branding.contactConfig = request.contactConfig?.let { venueWebsiteMapper.toDomain(it) }

        val saved = venueBrandingRepository.save(branding)
        return venueWebsiteMapper.toDto(saved)
    }
}
