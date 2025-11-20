package app.venues.finance.service

import app.venues.finance.domain.MerchantProfile
import app.venues.finance.repository.MerchantProfileRepository
import app.venues.organization.domain.Organization
import app.venues.organization.repository.OrganizationRepository
import app.venues.venue.domain.Venue
import app.venues.venue.repository.VenueRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class PaymentRoutingServiceTest {

    private val venueRepository = mockk<VenueRepository>()
    private val organizationRepository = mockk<OrganizationRepository>()
    private val merchantProfileRepository = mockk<MerchantProfileRepository>()

    private val service = PaymentRoutingService(
        venueRepository,
        organizationRepository,
        merchantProfileRepository
    )

    @Test
    fun `should resolve venue override profile`() {
        val venueId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val venue = mockk<Venue>()
        val profile = mockk<MerchantProfile>()

        every { venueRepository.findById(venueId) } returns Optional.of(venue)
        every { venue.merchantProfileId } returns profileId
        every { merchantProfileRepository.findById(profileId) } returns Optional.of(profile)

        val result = service.resolveMerchant(venueId)

        assertEquals(profile, result)
    }

    @Test
    fun `should fallback to organization default profile`() {
        val venueId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val venue = mockk<Venue>()
        val organization = mockk<Organization>()
        val profile = mockk<MerchantProfile>()

        every { venueRepository.findById(venueId) } returns Optional.of(venue)
        every { venue.merchantProfileId } returns null
        every { venue.organizationId } returns organizationId

        every { organizationRepository.findById(organizationId) } returns Optional.of(organization)
        every { organization.defaultMerchantProfileId } returns profileId

        every { merchantProfileRepository.findById(profileId) } returns Optional.of(profile)

        val result = service.resolveMerchant(venueId)

        assertEquals(profile, result)
    }

    @Test
    fun `should throw exception if no profile found`() {
        val venueId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val venue = mockk<Venue>()
        val organization = mockk<Organization>()

        every { venueRepository.findById(venueId) } returns Optional.of(venue)
        every { venue.merchantProfileId } returns null
        every { venue.organizationId } returns organizationId

        every { organizationRepository.findById(organizationId) } returns Optional.of(organization)
        every { organization.defaultMerchantProfileId } returns null

        assertThrows<IllegalStateException> {
            service.resolveMerchant(venueId)
        }
    }
}
