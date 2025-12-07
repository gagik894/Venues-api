package app.venues.venue.service

import app.venues.common.exception.VenuesException
import app.venues.location.domain.City
import app.venues.location.domain.Region
import app.venues.venue.api.dto.*
import app.venues.venue.api.mapper.VenueWebsiteMapper
import app.venues.venue.domain.HeroConfig
import app.venues.venue.domain.Venue
import app.venues.venue.domain.VenueBranding
import app.venues.venue.repository.VenueBrandingRepository
import app.venues.venue.repository.VenueRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class VenueWebsiteServiceTest {

    private val venueRepository: VenueRepository = mockk()
    private val venueBrandingRepository: VenueBrandingRepository = mockk()
    private val venueWebsiteMapper: VenueWebsiteMapper = mockk()
    private val venueRevalidationService: VenueRevalidationService = mockk(relaxed = true)

    private lateinit var service: VenueWebsiteService

    private val venueId = UUID.randomUUID()
    private lateinit var testVenue: Venue

    @BeforeEach
    fun setup() {
        service = VenueWebsiteService(
            venueRepository,
            venueBrandingRepository,
            venueWebsiteMapper,
            venueRevalidationService
        )

        val region = Region(code = "AM-ER", names = mapOf("en" to "Yerevan"))
        val city = City(region = region, slug = "yerevan", names = mapOf("en" to "Yerevan"))
        testVenue = Venue(
            name = "Test Venue",
            description = "A test venue",
            slug = "test-venue",
            organizationId = UUID.randomUUID(),
            address = "123 Test St",
            city = city
        )
    }

    @Nested
    inner class GetVenueBranding {

        @Test
        fun `returns branding when found`() {
            val branding = VenueBranding(venue = testVenue).apply {
                primaryColor = "#FF0000"
                secondaryColor = "#00FF00"
            }
            val expectedDto = VenueBrandingDto(
                venueId = venueId,
                primaryColor = "#FF0000",
                secondaryColor = "#00FF00",
                faviconUrl = null,
                homeHero = null,
                aboutBlocks = null,
                contactConfig = null,
                venueName = "Test Venue",
                logoUrl = null,
                coverImageUrl = null,
                socialLinks = null,
                contactEmail = null,
                phoneNumber = null,
                address = "123 Test St",
                website = null,
                latitude = null,
                longitude = null
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueBrandingRepository.findById(venueId) } returns Optional.of(branding)
            every { venueWebsiteMapper.toDto(branding) } returns expectedDto

            val result = service.getVenueBranding(venueId)

            assertEquals("#FF0000", result.primaryColor)
            assertEquals("#00FF00", result.secondaryColor)
            verify { venueBrandingRepository.findById(venueId) }
        }

        @Test
        fun `returns venue data when branding missing`() {
            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueBrandingRepository.findById(venueId) } returns Optional.empty()
            every { venueWebsiteMapper.toDto(any<VenueBranding>()) } answers {
                val branding = firstArg<VenueBranding>()
                VenueBrandingDto(
                    venueId = branding.venue.id,
                    primaryColor = branding.primaryColor,
                    secondaryColor = branding.secondaryColor,
                    faviconUrl = branding.faviconUrl,
                    homeHero = null,
                    aboutBlocks = null,
                    contactConfig = null,
                    venueName = branding.venue.name,
                    logoUrl = branding.venue.logoUrl,
                    coverImageUrl = branding.venue.coverImageUrl,
                    socialLinks = branding.venue.socialLinks,
                    contactEmail = branding.venue.contactEmail,
                    phoneNumber = branding.venue.phoneNumber,
                    address = branding.venue.address,
                    website = branding.venue.website,
                    latitude = branding.venue.latitude,
                    longitude = branding.venue.longitude
                )
            }

            val fallback = service.getVenueBranding(venueId)

            assertEquals("Test Venue", fallback.venueName)
            assertEquals("123 Test St", fallback.address)
            assertNull(fallback.primaryColor)
        }
    }

    @Nested
    inner class UpdateVenueBranding {

        @Test
        fun `creates new branding when none exists`() {
            val request = UpdateVenueBrandingRequest(
                primaryColor = "#123456",
                secondaryColor = "#654321",
                faviconUrl = "https://example.com/favicon.ico",
                homeHero = null,
                aboutBlocks = null,
                contactConfig = null
            )
            val expectedDto = VenueBrandingDto(
                venueId = venueId,
                primaryColor = "#123456",
                secondaryColor = "#654321",
                faviconUrl = "https://example.com/favicon.ico",
                homeHero = null,
                aboutBlocks = null,
                contactConfig = null,
                venueName = "Test Venue",
                logoUrl = null,
                coverImageUrl = null,
                socialLinks = null,
                contactEmail = null,
                phoneNumber = null,
                address = "123 Test St",
                website = null,
                latitude = null,
                longitude = null
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueBrandingRepository.findById(venueId) } returns Optional.empty()
            every { venueBrandingRepository.save(any<VenueBranding>()) } answers { firstArg() }
            every { venueWebsiteMapper.toDto(any<VenueBranding>()) } returns expectedDto

            val result = service.updateVenueBranding(venueId, request)

            assertEquals("#123456", result.primaryColor)
            verify { venueBrandingRepository.save(match<VenueBranding> { it.primaryColor == "#123456" }) }
        }

        @Test
        fun `updates existing branding`() {
            val existingBranding = VenueBranding(venue = testVenue).apply {
                primaryColor = "#OLD000"
            }
            val request = UpdateVenueBrandingRequest(
                primaryColor = "#NEW000",
                secondaryColor = null,
                faviconUrl = null,
                homeHero = null,
                aboutBlocks = null,
                contactConfig = null
            )
            val expectedDto = VenueBrandingDto(
                venueId = venueId,
                primaryColor = "#NEW000",
                secondaryColor = null,
                faviconUrl = null,
                homeHero = null,
                aboutBlocks = null,
                contactConfig = null,
                venueName = "Test Venue",
                logoUrl = null,
                coverImageUrl = null,
                socialLinks = null,
                contactEmail = null,
                phoneNumber = null,
                address = "123 Test St",
                website = null,
                latitude = null,
                longitude = null
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueBrandingRepository.findById(venueId) } returns Optional.of(existingBranding)
            every { venueBrandingRepository.save(any<VenueBranding>()) } answers { firstArg() }
            every { venueWebsiteMapper.toDto(any<VenueBranding>()) } returns expectedDto

            val result = service.updateVenueBranding(venueId, request)

            assertEquals("#NEW000", result.primaryColor)
            verify { venueBrandingRepository.save(match<VenueBranding> { it.primaryColor == "#NEW000" }) }
        }

        @Test
        fun `updates venue layout fields along with branding`() {
            val existingBranding = VenueBranding(venue = testVenue)
            val request = UpdateVenueBrandingRequest(
                primaryColor = null,
                secondaryColor = null,
                faviconUrl = null,
                homeHero = null,
                aboutBlocks = null,
                contactConfig = null,
                venueName = "Updated Venue",
                logoUrl = "https://example.com/logo.png",
                coverImageUrl = "https://example.com/cover.png",
                socialLinks = mapOf("facebook" to "fb.com/venue"),
                contactEmail = "hello@example.com",
                phoneNumber = "+374111111",
                address = "456 Updated St",
                website = "https://venue.example.com",
                latitude = 40.0,
                longitude = 44.0
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueBrandingRepository.findById(venueId) } returns Optional.of(existingBranding)
            every { venueBrandingRepository.save(any<VenueBranding>()) } answers { firstArg() }
            every { venueWebsiteMapper.toDto(any<VenueBranding>()) } returns mockk(relaxed = true)

            service.updateVenueBranding(venueId, request)

            assertEquals("Updated Venue", testVenue.name)
            assertEquals("https://example.com/logo.png", testVenue.logoUrl)
            assertEquals("https://example.com/cover.png", testVenue.coverImageUrl)
            assertEquals(mapOf("facebook" to "fb.com/venue"), testVenue.socialLinks)
            assertEquals("hello@example.com", testVenue.contactEmail)
            assertEquals("+374111111", testVenue.phoneNumber)
            assertEquals("456 Updated St", testVenue.address)
            assertEquals("https://venue.example.com", testVenue.website)
            assertEquals(40.0, testVenue.latitude)
            assertEquals(44.0, testVenue.longitude)
        }

        @Test
        fun `throws ResourceNotFound when venue not found`() {
            val request = UpdateVenueBrandingRequest(
                primaryColor = "#123456",
                secondaryColor = null,
                faviconUrl = null,
                homeHero = null,
                aboutBlocks = null,
                contactConfig = null
            )

            every { venueRepository.findById(venueId) } returns Optional.empty()

            val exception = assertThrows<VenuesException.ResourceNotFound> {
                service.updateVenueBranding(venueId, request)
            }

            assertTrue(exception.message!!.contains("Venue not found"))
        }

        @Test
        fun `updates branding with hero config`() {
            val heroDto = HeroConfigDto(
                title = mapOf("en" to "Welcome"),
                imageUrl = "https://example.com/hero.jpg",
                subtitle = mapOf("en" to "Subtitle"),
                ctaText = mapOf("en" to "Book Now"),
                ctaLink = "/book"
            )
            val heroDomain = HeroConfig(
                title = mapOf("en" to "Welcome"),
                imageUrl = "https://example.com/hero.jpg",
                subtitle = mapOf("en" to "Subtitle"),
                ctaText = mapOf("en" to "Book Now"),
                ctaLink = "/book"
            )
            val request = UpdateVenueBrandingRequest(
                primaryColor = null,
                secondaryColor = null,
                faviconUrl = null,
                homeHero = heroDto,
                aboutBlocks = null,
                contactConfig = null
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueBrandingRepository.findById(venueId) } returns Optional.empty()
            every { venueBrandingRepository.save(any<VenueBranding>()) } answers { firstArg() }
            every { venueWebsiteMapper.toDomain(heroDto) } returns heroDomain
            every { venueWebsiteMapper.toDto(any<VenueBranding>()) } returns mockk(relaxed = true)

            service.updateVenueBranding(venueId, request)

            verify { venueBrandingRepository.save(match<VenueBranding> { it.homeHero == heroDomain }) }
        }
    }

    @Nested
    inner class GetWebsiteLayout {

        @Test
        fun `returns layout for valid venue`() {
            val expectedLayout = WebsiteLayoutDto(
                language = "en",
                theme = WebsiteThemeDto("#FF0000", "#00FF00", null),
                header = WebsiteHeaderDto("Test Venue", null, null),
                footer = WebsiteFooterDto(null, null, null, "123 Test St", "Yerevan")
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueWebsiteMapper.toLayoutDto(testVenue, "en") } returns expectedLayout

            val result = service.getWebsiteLayout(venueId, "en")

            assertEquals("en", result.language)
            assertEquals("Test Venue", result.header.venueName)
        }

        @Test
        fun `throws ResourceNotFound when venue not found`() {
            every { venueRepository.findById(venueId) } returns Optional.empty()

            assertThrows<VenuesException.ResourceNotFound> {
                service.getWebsiteLayout(venueId, "en")
            }
        }
    }

    @Nested
    inner class GetHomePage {

        @Test
        fun `returns home page for valid venue`() {
            val expectedPage = HomePageDto(hero = null)

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueWebsiteMapper.toHomePageDto(testVenue, "en") } returns expectedPage

            val result = service.getHomePage(venueId, "en")

            assertNull(result.hero)
        }
    }

    @Nested
    inner class GetAboutPage {

        @Test
        fun `returns about page for valid venue`() {
            val expectedPage = AboutPageDto(blocks = emptyList())

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueWebsiteMapper.toAboutPageDto(testVenue, "en") } returns expectedPage

            val result = service.getAboutPage(venueId, "en")

            assertTrue(result.blocks.isEmpty())
        }
    }

    @Nested
    inner class GetContactPage {

        @Test
        fun `returns contact page for valid venue`() {
            val expectedPage = ContactPageDto(
                address = "123 Test St",
                city = "Yerevan",
                coordinates = null,
                contactInfo = ContactInfoDto(null, null, null),
                schedule = emptyList(),
                mapConfig = null
            )

            every { venueRepository.findById(venueId) } returns Optional.of(testVenue)
            every { venueWebsiteMapper.toContactPageDto(testVenue, "en") } returns expectedPage

            val result = service.getContactPage(venueId, "en")

            assertEquals("123 Test St", result.address)
            assertEquals("Yerevan", result.city)
        }
    }
}
