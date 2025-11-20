package app.venues.finance.service

import app.venues.finance.domain.MerchantProfile
import app.venues.finance.repository.MerchantProfileRepository
import app.venues.organization.api.OrganizationApi
import app.venues.organization.api.dto.OrganizationDto
import app.venues.venue.api.VenueApi
import app.venues.venue.api.dto.VenueBasicInfoDto
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class PaymentRoutingServiceTest {

    private val venueApi = mockk<VenueApi>()
    private val organizationApi = mockk<OrganizationApi>()
    private val merchantProfileRepository = mockk<MerchantProfileRepository>()

    private val service = PaymentRoutingService(
        venueApi,
        organizationApi,
        merchantProfileRepository
    )

    @Test
    fun `should resolve venue override profile`() {
        val venueId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val profile = mockk<MerchantProfile>()

        val venueInfo = VenueBasicInfoDto(
            id = venueId,
            name = "Test Venue",
            address = "123 Main St",
            latitude = 0.0,
            longitude = 0.0,
            organizationId = organizationId,
            merchantProfileId = profileId
        )

        every { venueApi.getVenueBasicInfo(venueId) } returns venueInfo
        every { merchantProfileRepository.findById(profileId) } returns Optional.of(profile)

        // Mock profile fields for DTO conversion
        every { profile.id } returns profileId
        every { profile.name } returns "Test Merchant"
        every { profile.legalName } returns "Test Legal Name"
        every { profile.taxId } returns "12345"
        every { profile.organizationId } returns organizationId
        every { profile.hasPaymentConfig() } returns true

        val result = service.resolveMerchant(venueId)

        assertEquals(profile.id, result.id)
        assertEquals(profile.name, result.name)
        assertEquals(profile.organizationId, result.organizationId)
    }

    @Test
    fun `should fallback to organization default profile`() {
        val venueId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val profile = mockk<MerchantProfile>()

        val venueInfo = VenueBasicInfoDto(
            id = venueId,
            name = "Test Venue",
            address = "123 Main St",
            latitude = 0.0,
            longitude = 0.0,
            organizationId = organizationId,
            merchantProfileId = null
        )

        val organizationDto = OrganizationDto(
            id = organizationId,
            name = "Test Org",
            slug = "test-org",
            defaultMerchantProfileId = profileId
        )

        every { venueApi.getVenueBasicInfo(venueId) } returns venueInfo
        every { organizationApi.getOrganization(organizationId) } returns organizationDto
        every { merchantProfileRepository.findById(profileId) } returns Optional.of(profile)

        // Mock profile fields for DTO conversion
        every { profile.id } returns profileId
        every { profile.name } returns "Test Merchant"
        every { profile.legalName } returns "Test Legal Name"
        every { profile.taxId } returns "12345"
        every { profile.organizationId } returns organizationId
        every { profile.hasPaymentConfig() } returns true

        val result = service.resolveMerchant(venueId)

        assertEquals(profile.id, result.id)
        assertEquals(profile.name, result.name)
    }

    @Test
    fun `should throw exception if no profile found`() {
        val venueId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()

        val venueInfo = VenueBasicInfoDto(
            id = venueId,
            name = "Test Venue",
            address = "123 Main St",
            latitude = 0.0,
            longitude = 0.0,
            organizationId = organizationId,
            merchantProfileId = null
        )

        val organizationDto = OrganizationDto(
            id = organizationId,
            name = "Test Org",
            slug = "test-org",
            defaultMerchantProfileId = null
        )

        every { venueApi.getVenueBasicInfo(venueId) } returns venueInfo
        every { organizationApi.getOrganization(organizationId) } returns organizationDto

        assertThrows<IllegalStateException> {
            service.resolveMerchant(venueId)
        }
    }
}
