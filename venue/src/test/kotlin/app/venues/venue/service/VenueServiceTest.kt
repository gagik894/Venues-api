package app.venues.venue.service

import app.venues.location.domain.City
import app.venues.location.domain.Region
import app.venues.venue.api.dto.UpdateVenueRequest
import app.venues.venue.api.dto.VenueAdminResponse
import app.venues.venue.api.mapper.VenueMapper
import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenueStatus
import app.venues.venue.repository.VenueCategoryRepository
import app.venues.venue.repository.VenueRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.*

class VenueServiceTest {

    private val venueRepository: VenueRepository = mockk(relaxed = true)
    private val cityRepository = mockk<app.venues.location.repository.CityRepository>(relaxed = true)
    private val categoryRepository = mockk<VenueCategoryRepository>(relaxed = true)
    private val venueMapper: VenueMapper = mockk(relaxed = true)
    private val promoCodeService: VenuePromoCodeService = mockk(relaxed = true)
    private val venueSettingsService: VenueSettingsService = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)

    private val venueService = VenueService(
        venueRepository,
        cityRepository,
        categoryRepository,
        venueMapper,
        promoCodeService,
        venueSettingsService,
        eventPublisher
    )

    @Test
    fun `getVenueByDomain returns null for non-active venues`() {
        every { venueRepository.findByCustomDomainAndStatus("opera.am", VenueStatus.ACTIVE) } returns null

        val result = venueService.getVenueByDomain("opera.am")

        assertNull(result)
        verify(exactly = 1) { venueRepository.findByCustomDomainAndStatus("opera.am", VenueStatus.ACTIVE) }
    }

    @Test
    fun `getVenueByDomain returns basic info for active venues`() {
        val venueId = UUID.randomUUID()
        val venue = mockk<Venue>(relaxed = true) {
            every { id } returns venueId
            every { name } returns "Opera"
            every { slug } returns "opera"
            every { address } returns "Addr"
            every { latitude } returns null
            every { longitude } returns null
            every { organizationId } returns UUID.randomUUID()
            every { merchantProfileId } returns null
        }
        every { venueRepository.findByCustomDomainAndStatus("opera.am", VenueStatus.ACTIVE) } returns venue

        val result = venueService.getVenueByDomain("opera.am")

        assertEquals(venueId, result?.id)
        verify(exactly = 1) { venueRepository.findByCustomDomainAndStatus("opera.am", VenueStatus.ACTIVE) }
    }

    @Test
    fun `updateVenue publishes domain change event when custom domain changes`() {
        val region = Region(code = "AM-ER", names = mapOf("en" to "Yerevan"))
        val city = City(region = region, slug = "yerevan", names = mapOf("en" to "Yerevan"))
        val venue = Venue(
            name = "Opera",
            description = "desc",
            slug = "opera",
            organizationId = UUID.randomUUID(),
            address = "addr",
            city = city
        ).apply {
            customDomain = "old.example"
        }

        venue.suspend()

        val request = UpdateVenueRequest(customDomain = "new.example")

        every { venueRepository.findById(any()) } returns Optional.of(venue)
        every { venueRepository.save(any()) } answers { firstArg() }
        every { venueMapper.updateEntity(any(), any(), any(), any()) } answers {
            val entity = firstArg<Venue>()
            val req = secondArg<UpdateVenueRequest>()
            entity.customDomain = req.customDomain
        }
        every { venueMapper.toAdminResponse(any()) } answers {
            val saved = firstArg<Venue>()
            VenueAdminResponse(
                id = saved.id,
                slug = saved.slug,
                name = saved.name,
                legalName = null,
                taxId = null,
                description = saved.description,
                address = saved.address,
                citySlug = saved.city.slug,
                cityName = saved.city.slug,
                latitude = saved.latitude,
                longitude = saved.longitude,
                timeZone = saved.timeZone,
                categoryCode = null,
                categoryName = null,
                phoneNumber = saved.phoneNumber,
                website = saved.website,
                contactEmail = saved.contactEmail,
                socialLinks = saved.socialLinks,
                ownershipType = saved.ownershipType,
                notificationEmails = saved.notificationEmails,
                logoUrl = saved.logoUrl,
                coverImageUrl = saved.coverImageUrl,
                customDomain = saved.customDomain,
                isAlwaysOpen = saved.isAlwaysOpen,
                status = saved.status,
                createdAt = Instant.EPOCH,
                lastModifiedAt = Instant.EPOCH
            )
        }

        venueService.updateVenue(venue.id, request)

        verify {
            eventPublisher.publishEvent(match<app.venues.shared.web.context.DomainChangedEvent> {
                it.venueId == venue.id && it.oldDomain == "old.example" && it.newDomain == "new.example"
            })
        }
    }
}
